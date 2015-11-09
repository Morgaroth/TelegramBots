package io.github.morgaroth.telegram.bot.bots.callout

import akka.actor.{Actor, ActorLogging, Props, Stash}
import akka.event.LoggingAdapter
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.global.ctx
import com.typesafe.config.Config
import io.github.morgaroth.telegram.bot.core.api.models.extractors._
import io.github.morgaroth.telegram.bot.core.api.models._
import io.github.morgaroth.telegram.bot.core.engine.NewUpdate
import io.github.morgaroth.utils.mongodb.salat.MongoDAO
import org.bson.types.ObjectId

import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls

/**
  * Created by mateusz on 22.10.15.
  */

case class CallOutGroup(
                         chat: UberUser,
                         group: String,
                         members: Set[String] = Set.empty,
                         _id: Option[ObjectId]
                       )

trait CallOutGroupDao {
  def config: Config

  def collection: Option[String] = None

  lazy val dao = {
    val d = new MongoDAO[CallOutGroup](config, collection.getOrElse("groups")) {
    }
    //places for indexes
    d
  }

  def findGroups(chat: Chat) = dao.find(MongoDBObject("chat.id" -> chat.chatId)).map(_.group)

  def findGroup(group: String, chat: Chat): Option[CallOutGroup] = {
    dao.findOne(MongoDBObject("chat.id" -> chat.chatId, "group" -> group))
  }

  def getUserGroupsInChat(chat: Chat, username: String) = {
    dao.find(MongoDBObject(
      "chat.id" -> chat.chatId,
      "members" -> username
    ))
  }

  def findUserGroups(username: String) = {
    dao.find(MongoDBObject("members" -> username)).toList
  }

  def removeUserFromChat(chat: Chat, username: String)(implicit log: LoggingAdapter) = {
    log.info(s"removing user $username from all groups in $chat")
    dao.update(
      MongoDBObject("chat.id" -> chat.chatId, "members" -> username),
      MongoDBObject("$pull" -> MongoDBObject("members" -> username)),
      multi = true
    )
  }

  def addUserToGroup(group: String, chat: Chat, user: String)(implicit log: LoggingAdapter): Unit = {
    findGroup(group, chat).map { ex =>
      if (!ex.members.contains(user)) {
        val r = dao.update(MongoDBObject("_id" -> ex._id.get), MongoDBObject("$addToSet" -> MongoDBObject("members" -> s"$user")))
        log.info(s"user $user added to group $group in chat $chat")
        r
      }
    } getOrElse {
      dao.save(CallOutGroup(chat.uber, group, Set(user), None))
      log.info(s"user $user added to new group $group in chat $chat")
    }
  }


  def removeUserFromGroup(group: String, chat: Chat, username: String)(implicit log: LoggingAdapter, exCtx: ExecutionContext): Unit = {
    findGroup(group, chat).map { ex =>
      val r = dao.update(MongoDBObject("_id" -> ex._id.get), MongoDBObject("$pull" -> MongoDBObject("members" -> username)))
      log.info(s"user $username removed from group $group in chat $chat")
      Future(dropEmptyGroups)
      r
    }
  }

  def dropEmptyGroups = {
    dao.remove(MongoDBObject("members.0" -> MongoDBObject("$exists" -> false)))
  }
}

object CallOutBot {

  def props(cfg: Config) = Props(classOf[CallOutBot], cfg)
}

class CallOutBot(cfg: Config) extends Actor with ActorLogging with Stash {

  import context.dispatcher

  implicit val la: LoggingAdapter = log

  val dao = new CallOutGroupDao {
    override def config: Config = cfg.getConfig("database")
  }

  var me: User = null
  var myUserName: String = null

  override def receive: Receive = {
    case u: User =>
      me = u
      myUserName = u.username.get
      log.info(s"I'm a $u")
      context become working
      unstashAll()
    case _ =>
      stash()
  }

  def working: Receive = {
    case SingleArgCommand("_add_me", group, (chat, from, _)) if from.username.isDefined && chat.isGroupChat =>
      println(s"group $group")
      addUserToGroup("all", chat, from.username.get)
      addUserToGroup(group, chat, from.username.get)

    case MultiArgCommand("_add", (user :: group :: Nil), (chat, from, _)) if chat.isGroupChat =>
      (user.stripPrefix("@") :: from.username.toList).foreach(u => addUserToGroup("all", chat, u))
      addUserToGroup(group, chat, user.stripPrefix("@"))

    case SingleArgCommand("_remove_me", group, (chat, from, _)) if from.username.isDefined && group != "all" && chat.isGroupChat =>
      addUserToGroup("all", chat, from.username.get)
      dao.removeUserFromGroup(group, chat, from.username.get)

    case NewChatParticipant(user, (chat, from, _)) =>
      log.info( s"""new chat participant $user in chat $chat, adding them to "all"""")
      (from.username.toList ::: user.username.toList).foreach(u => addUserToGroup("all", chat, u))

    case RemovedParticipant(user, date, (chat, from, _)) if user.username.isDefined =>
      log.info(s"removed chat participant $user from $chat")
      dao.removeUserFromChat(chat, user.username.get)

    case SingleArgCommand("_remove_me", group, (chat, from, _)) if from.username.isDefined && chat.isGroupChat =>
      sender() ! chat.msg("You cannot remove self from all group, all is all.")

    case SingleArgCommand("_members", group, (chat, from, _)) if chat.isGroupChat =>
      val msg = dao.findGroup(group, chat).map(_.members.mkString(s"*$group*: ", ", ", "")).getOrElse(s"Group *$group* doesn't exist.")
      sender() ! chat.msg(msg, parse_mode = Some("Markdown"))

    case NoArgCommand("_my_groups", (chat, from, _)) if chat.isPrvChat && from.username.isDefined =>
      val userGroups = dao.findUserGroups(from.username.get)
      val msg = if (userGroups.isEmpty) "You have no groups."
      else {
        userGroups.groupBy(_.chat.id).mapValues(x => x.head.chat -> x.map(_.group)).values.map {
          case (ch, grps) => s"*${ch.firstName}*: ${grps.mkString(", ")}"
        }.mkString("- ", "\n- ", "")
      }
      sender() ! chat.msg(msg, parse_mode = Some("Markdown"))

    case NoArgCommand("_list_groups", (chat, from, _)) if chat.isGroupChat =>
      val grps = dao.findGroups(chat).toList
      val msg = if (grps.isEmpty) {
        "There is no groups"
      } else {
        grps.mkString("Groups in this chat:\n    ✔", "\n    ✔", "")
      }
      sender() ! SendMessage(chat.chatId, msg)

    case MultiArgCommand(groupName, _, (chat, from, _)) if chat.isGroupChat && !groupName.startsWith("_") =>
      dao.findGroup(groupName, chat)
        .map(x => x.members -- from.username.toSet)
        .filter(_.nonEmpty)
        .foreach { g =>
          val msg = g.mkString("Call @", ", @", "")
          sender() ! SendMessage(chat.chatId, msg, parse_mode = Some("Markdown"))
        }

    case NoArgCommand("start", (ch, _, _)) =>
      sender() ! SendMessage(ch.chatId, "Hello, this bot is under implementation")

    case NewUpdate(_, _, u: Update) if u.message.from.username.isDefined && u.message.chat.isGroupChat =>
      println(s"msg from ${u.message.from}")
      addUserToGroup("all", u.message.chat, u.message.from.username.get)
  }

  def addUserToGroup(group: String, chat: Chat, user: String): Unit = {
    if (user != myUserName) {
      val valid = """[^a-zA-Z0-9]""".r.findFirstMatchIn(group)
      if (valid.nonEmpty) {
        sender() ! SendMessage(chat.chatId, s"Illegal character at position ${valid.head.start} (${valid.head.group(0)}), only alphanumeric allowed.")
      } else {
        dao.addUserToGroup(group, chat, user)
      }
    }
  }
}
