package io.github.morgaroth.telegram.bot.bots.boobsbot

import java.io.{File => JFile}
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingAdapter
import com.mongodb.MongoException.DuplicateKey
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.annotations.Key
import com.novus.salat.global.ctx
import com.typesafe.config.{Config, ConfigFactory}
import io.github.morgaroth.telegram.bot.bots.boobsbot.CyckoBot.PublishBoobs
import io.github.morgaroth.telegram.bot.bots.boobsbot.FetchAndCalculateHash.{NoContentInformation, UnsupportedBoobsContent}
import io.github.morgaroth.telegram.bot.core.api.methods.Response
import io.github.morgaroth.telegram.bot.core.api.models._
import io.github.morgaroth.telegram.bot.core.api.models.extractors.{MultiArgCommand, NoArgCommand, SingleArgCommand}
import io.github.morgaroth.telegram.bot.core.engine.NewUpdate
import io.github.morgaroth.telegram.bot.core.engine.core.BotActor._
import io.github.morgaroth.utils.mongodb.salat.MongoDAO
import org.joda.time.DateTime

import scala.language.reflectiveCalls
import scala.util.{Failure, Success, Try}

case class BoobsListener(@Key("_id") id: String, chatId: Int, created: DateTime = DateTime.now())

trait BoobsListenerDao {
  def config: Config

  def collection: Option[String] = None

  lazy val dao = new MongoDAO[BoobsListener](config, collection.getOrElse("listeners")) {}
}

object CyckoBot {

  private[CyckoBot] case class PublishBoobs(f: Boobs, owner: Chat, worker: Option[ActorRef] = None)

  def props() =
    Props(classOf[CyckoBot])
}

class CyckoBot extends Actor with ActorLogging {

  import context.dispatcher
  import context.system

  implicit def implLog: LoggingAdapter = log

  val hardSelf = self

  val FilesDao = new BoobsDao {
    override def config = ConfigFactory.parseString( """uri = "mongodb://localhost/CyckoBot" """)
  }
  val SubsDao = new BoobsListenerDao {
    override def config = ConfigFactory.parseString( """uri = "mongodb://localhost/CyckoBot" """)
  }


  override def receive: Receive = {
    case SingleArgCommand("resolve", arg, (chat, _, _)) =>
      resolveLink(arg, chat, sender())

    case SingleArgCommand("add", arg, (chat, _, _)) =>
      resolveLink(arg, chat, sender(), publish = false)

    case NoArgCommand("stats", (chat, _, _)) =>
      sender() ! SendMessage(chat.chatId,
        s"""Status are:
           |* all files in database = ${FilesDao.dao.count()}
           |* all subscribers in database = ${SubsDao.dao.count()}
           |
          |This is all for now.
      """.stripMargin)

    case NoArgCommand("all", (ch, user, _)) if user.id == 36792931 =>
      FilesDao.dao.find(MongoDBObject.empty).foreach(f =>
        sender() ! SendBoobsCorrectType(ch.chatId, f)
      )

    case MultiArgCommand("updatedb", args, (ch, user, _)) if user.id == 36792931 =>
      val sen = sender()
      Try {
        val f = args.map(_.toInt) match {
          case from :: to :: Nil =>
            println(s"from $from to $to")
            LinksFromTumblrFetch.runAsync("boobsinmotion", from, Some(to))
          case from :: Nil => LinksFromTumblrFetch.runAsync("boobsinmotion", from)
          case Nil => LinksFromTumblrFetch.runAsync("boobsinmotion", 0)
        }
        f.onComplete {
          case Success(_) =>
            sen ! SendMessage(ch.chatId, s"fetching BoobsinMotion blog with args $args end with success")
          case Failure(t) =>
            sen ! SendMessage(ch.chatId, s"fetching BoobsinMotion blog with args $args end with error: ${t.getMessage}")
            log.error(t, "fetching images")
        }
      }.recover {
        case t =>
          sen ! SendMessage(ch.chatId, s"parsing arguments for command fetching blog end with error ${t.getMessage}")
          log.error(t, "parsing arguments")
      }

    case NewUpdate(id, _, u@Update(_, m)) if m.text.isDefined =>
      val g = m.text.get
      g.stripPrefix("/") match {
        case start if start.startsWith("start") =>
          sendHelp(m.chatId)
        case getImages if getImages.startsWith("get") =>
          val numberStr = getImages.stripPrefix("get").dropWhile(_ == " ").takeWhile(_ != " ")
          log.info(s"parsed number $numberStr")
          val count = Math.min(Try(numberStr.trim.toInt).toOption.getOrElse(1), 5)
          FilesDao.random(count).foreach(fId =>
            sender() ! SendBoobsCorrectType(m.chatId, fId)
          )

        case getRandom if getRandom.startsWith("random") =>
          FilesDao.random(1).foreach(fId =>
            sender() ! SendBoobsCorrectType(m.chatId, fId)
          )
        case subscribe if subscribe.startsWith("subscribe") =>
          val response = Try {
            SubsDao.dao.insert(BoobsListener(m.chatId.toString, m.chatId))
            "This chat is subscribed!"
          }.recover {
            case e: DuplicateKey => "This chat is already subscribed"
            case anot => s"Another error ${anot.getMessage}"
          }.get
          sender() ! SendMessage(m.chatId, response)

        case subscribe if subscribe.startsWith("unsubscribe") =>
          val wr = SubsDao.dao.remove(MongoDBObject("chatId" -> m.chatId))
          val response = if (wr.getN == 0) "Unsubscribed (newer was)" else "Unsubscribed :("
          sender() ! SendMessage(m.chatId, response)

        case delete if delete.startsWith("delete") =>
          if (m.reply_to_message.isDefined) {
            if (m.reply_to_message.get.document.isDefined) {
              val toRemove = m.reply_to_message.get.document.get.file_id
              FilesDao.dao.remove(MongoDBObject("_id" -> toRemove))
              sender() ! SendMessage(m.chatId, "Deleted.")
            } else if (m.reply_to_message.get.photo.isDefined) {
              val photos = m.reply_to_message.get.photo.get
              val biggest: String = photos.sortBy(_.width).last.file_id
              FilesDao.dao.remove(MongoDBObject("_id" -> biggest))
              sender() ! SendMessage(m.chatId, "Deleted.")
            } else {
              sender() ! SendMessage(m.chatId, "Sorry, this message isn't a photo/file comment of reply.")
            }
          } else {
            sender() ! SendMessage(m.chatId, "Sorry, this message isn't a comment of reply.")
          }
        case help if help.startsWith("help") =>
          sendHelp(m.chatId)
        case illegal if m.reply_to_message.isDefined =>
        // ignore, someone only commented Bot message
        case unknown =>
          sender() ! SendMessage(m.chatId, s"Sorry, I dont know command $unknown.")
      }
      sender() ! Handled(id)

    // wyłapywanie obrazków
    case NewUpdate(id, _, u@Update(_, m)) if m.photo.isDefined =>
      // catched photo
      val photos = m.photo.get
      val biggest: PhotoSize = photos.sortBy(_.width).last
      val hardSender = sender()
      FilesDao.byId(biggest.file_id) match {
        case Some(prev) =>
          hardSender ! SendMessage(u.message.chatId, "This image is already in DataBase")
        case None =>
          hardSender ! SendMapped(GetFile(biggest.file_id), {
            case Response(_, Right(f@File(_, _, Some(fPath))), _) =>
              hardSender ! FetchFile(f, Boobs.photo, u.message.chat)
              log.info(s"got file $fPath, downloading started")
            case other =>
              log.warning(s"got other response $other")
          })
      }

    case FileFetchingResult(File(fId, _, _), author, t, Success(data)) =>
      log.info(s"file $fId fetched")
      val hash = calculateMD5(data)
      val info = FilesDao.byHash(hash) match {
        case Some(prev) => "This image is already in DataBase"
        case None =>
          Try {
            val files = Boobs(fId, t, hash, Some(author.uber))
            FilesDao.dao.insert(files)
            self.forward(PublishBoobs(files, author))
            "Thx!"
          }.recover {
            case e: DuplicateKey => "This image is already in DataBase"
            case anot => s"Another error ${anot.getMessage}"
          }.get
      }
      sender() ! SendMessage(author.chatId, info)

    case s: FileFetchingResult =>
      log.warning(s"error fetching file: $s")


    case NewUpdate(id, _, u@Update(_, m)) if m.document.isDefined =>
      val doc = m.document.get
      if (doc.mime_type.isDefined) {
        val mime = doc.mime_type.get
        if (Set("image/jpeg", "image/png", "image/gif") contains mime) {
          val hardSender = sender()
          FilesDao.byId(doc.file_id) match {
            case Some(prev) =>
              hardSender ! SendMessage(u.message.chatId, "This image is already in DataBase")
            case None =>
              hardSender ! SendMapped(GetFile(doc.file_id), {
                case Response(_, Right(f@File(_, _, Some(fPath))), _) =>
                  hardSender ! FetchFile(f, Boobs.document, u.message.chat)
                  log.info(s"got file $fPath, downloading started")
                case other =>
                  log.warning(s"got other response $other")
              })
          }
        } else {
          sender() ! HandledUpdate(id, SendMessage(m.chatId, s"Invalid ($mime) mime type."))
        }
      } else {
        sender() ! HandledUpdate(id, SendMessage(m.chatId, "Mime type not defined, I suck"))
      }

    case PublishBoobs(f, owner, None) =>
      self ! PublishBoobs(f, owner, Some(sender()))

    case PublishBoobs(f, owner, Some(worker)) =>
      log.info(s"publishing boobs $f")
      SubsDao.dao.find(MongoDBObject("chatId" -> MongoDBObject("$ne" -> owner))).foreach { listener =>
        worker ! SendBoobsCorrectType(listener.chatId, f)
      }
  }


  def sendHelp(chatId: Int): Unit = {
    val text =
      """
        |Hello, here CyckoBot, I will provide the *best boobs* for You!
        |
        |My boobs database is build by You, send me images, gifs and I save it for
        |You and Your camrades.
        |
        |Commands:
        |resolve - downloads gif from http link to gif, publishes new file to all subscribers
        |add - like resolve, but without publishing
        |stats - prints some DB statistics
        |get - returns random boobs
        |get - with argument `get N` returns N random boobs from database, max is 5
        |random - like get
        |subscribe - subscribes this chat to boobs news
        |unsubscribe - unsubscribes
        |delete - as reply to image - deletes boobs from DB
        |help - returns this help
      """.stripMargin
    sender() ! SendMessage(chatId, text, Some("Markdown"))
  }

  def SendBoobsCorrectType(to: Int, fId: Boobs): Command = {
    if (fId.typ == Boobs.document) SendDocument(to, Right(fId.fileId)) else SendPhoto(to, Right(fId.fileId))
  }

  def doSthWithNewFile(chatId: Chat, worker: ActorRef, files: Boobs, publish: Boolean = true) = {
    Try(FilesDao.dao.insert(files)).map(x => log.info(s"saved to db $files")).getOrElse {
      log.warning(s"not saved $files")
    }
    if (publish) {
      hardSelf ! PublishBoobs(files, chatId, Some(worker))
    }
  }

  def resolveLink(link: String, user: Chat, bot: ActorRef, publish: Boolean = true): Unit = {
    FetchAndCalculateHash(link).map { case (hash, tmpFile) =>
      FilesDao.byHash(hash) match {
        case Some(previous) =>
          bot ! SendMessage(user.chatId, "This image is already in DataBase")
          tmpFile.delete()
        case None =>
          bot ! SendMapped(SendDocument(user.chatId, Left(tmpFile)), {
            case Response(true, Left(id), _) =>
              log.info(s"received $id, I know what it is")
              tmpFile.delete()
            case Response(true, Right(m: Message), _) if m.document.isDefined =>
              log.info(s"catched new boobs file ${m.document.get.file_id}")
              doSthWithNewFile(user, bot, Boobs(m.document.get.file_id, Boobs.document, hash, Some(user.uber)), publish)
              tmpFile.delete()
            case another =>
              log.warning(s"don't know what is this $another")
              tmpFile.delete()
          })
      }
    }
  }.recover {
    case UnsupportedBoobsContent(other) =>
      bot ! SendMessage(user.chatId, s"Can't recognize file, only accept image/gif, current is ${other.value}")
    case NoContentInformation =>
      bot ! SendMessage(user.chatId, s"Can't recognize file, service doesn't provide content type... WTF!?")
    case t =>
      t.printStackTrace()
  }

  def calculateMD5(f: Array[Byte]): String = {
    val md = MessageDigest.getInstance("MD5")
    md.update(f)
    DatatypeConverter.printHexBinary(md.digest())
  }

  def calculateMD5(f: JFile): String = {
    import better.files.Cmds.md5
    import better.files.{File => BFile}
    md5(BFile(f.getAbsolutePath))
  }
}
