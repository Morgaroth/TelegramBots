package io.github.morgaroth.telegram.bot.bots.groupalias

import akka.actor.{Actor, ActorLogging, Props, Stash}
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.global.ctx
import com.typesafe.config.Config
import io.github.morgaroth.telegram.bot.core.api.models.extractors.{MultiArgCommand, NoArgCommand, SingleArgCommand}
import io.github.morgaroth.telegram.bot.core.api.models.{Chat, SendMessage, User}
import io.github.morgaroth.utils.mongodb.salat.MongoDAO
import org.bson.types.ObjectId

import scala.concurrent.Future
import scala.language.reflectiveCalls

/**
  * Created by mateusz on 22.10.15.
  */

case class CallOutGroup(
                         chat: Int,
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

  def findGroups(chat: Chat) = dao.find(MongoDBObject("chat" -> chat.chatId)).map(_.group)

  def findGroup(group: String, chat: Chat): Option[CallOutGroup] = {
    dao.findOne(MongoDBObject("chat" -> chat.chatId, "group" -> group))
  }
}

object CallOutBot {

  def props(cfg: Config) = Props(classOf[CallOutBot], cfg)
}

class CallOutBot(cfg: Config) extends Actor with ActorLogging with Stash {

  import context.dispatcher

  val dao = new CallOutGroupDao {
    override def config: Config = cfg
  }

  var me: User = null
  var myUserName: String = null

  override def receive: Receive = {
    case u: User =>
      me = u
      myUserName = s"@${u.username.get}"
      context become working
      unstashAll()
    case _ =>
      stash()
  }

  def working: Receive = {
    case SingleArgCommand("_add_me", group, (chat, from, _)) if from.username.isDefined =>
      addUserToGroup(group, chat, from.username.map(x => s"@$x").get)

    case MultiArgCommand("_add", (user :: group :: Nil), (chat, from, _)) =>
      addUserToGroup(group, chat, s"@${user.stripPrefix("@")}")

    case SingleArgCommand("_remove_me", group, (chat, from, _)) if from.username.isDefined =>
      dao.findGroup(group, chat).map { ex =>
        val r = dao.dao.update(MongoDBObject("_id" -> ex._id.get), MongoDBObject("$pull" -> MongoDBObject("members" -> s"@${from.username.get}")))
        log.info(s"user ${from.getAnyUserName} removed from group $group in chat $chat")
        Future(dao.dao.remove(MongoDBObject("members.0" -> MongoDBObject("$exists" -> false))))
        r
      }

    case NoArgCommand("_my_groups", (chat, from, _)) if chat.isPrvChat =>
      sender() ! chat.msg("HTTP, 501")

    case NoArgCommand("_list_groups", (chat, from, _)) =>
      val grps = dao.findGroups(chat).toList
      val msg = if (grps.isEmpty) {
        "There is no groups"
      } else {
        grps.mkString("Groups in this channel:\n    ✔", "\n    ✔", "")
      }
      sender() ! SendMessage(chat.chatId, msg)

    case MultiArgCommand(groupName, _, (chat, from, _)) =>
      dao.findGroup(groupName, chat).foreach { g =>
        val msg = g.members.mkString("Call ", ", ", "")
        sender() ! SendMessage(chat.chatId, msg)
      }

    case NoArgCommand("start", (ch, _, _)) =>
      sender() ! SendMessage(ch.chatId, "Hello, this bot is under implementation")
  }

  def addUserToGroup(group: String, chat: Chat, user: String): Unit = {
    val valid = """[^a-zA-Z0-9]""".r.findFirstMatchIn(group)
    if (valid.nonEmpty) {
      sender() ! SendMessage(chat.chatId, s"Illegal character at position ${valid.head.start} (${valid.head.group(0)}), only alphanumeric allowed.")
    } else {
      dao.findGroup(group, chat).map { ex =>
        val r = dao.dao.update(MongoDBObject("_id" -> ex._id.get), MongoDBObject("$addToSet" -> MongoDBObject("members" -> s"$user")))
        log.info(s"user $user added to group $group in chat $chat")
        r
      } getOrElse {
        dao.dao.save(CallOutGroup(chat.chatId, group, Set(user), None))
        log.info(s"user $user added to new group $group in chat $chat")
      }
    }
  }
}
