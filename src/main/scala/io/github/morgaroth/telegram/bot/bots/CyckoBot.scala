package io.github.morgaroth.telegram.bot.bots

import java.io.{FileOutputStream, File}
import java.nio.file.Paths

import akka.actor.{ActorRef, Actor, ActorLogging, Props}
import com.mongodb.MongoException.DuplicateKey
import com.mongodb.casbah.Imports.mongoCollAsScala
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import com.mongodb.casbah.{Imports, MongoClient, MongoClientURI, WriteConcern}
import com.novus.salat.Context
import com.novus.salat.annotations.Key
import com.novus.salat.dao.SalatDAO
import com.novus.salat.global.ctx
import io.github.morgaroth.telegram.bot.bots.CyckoBot.PublishBoobs
import io.github.morgaroth.telegram.bot.core.api.methods.Response
import io.github.morgaroth.telegram.bot.core.api.models._
import io.github.morgaroth.telegram.bot.core.api.models.extractors.SingleArgCommandMessage
import io.github.morgaroth.telegram.bot.core.engine.NewUpdate
import io.github.morgaroth.telegram.bot.core.engine.core.BotActor.{SendMapped, Handled, HandledUpdate}
import org.joda.time.DateTime
import spray.client.pipelining._

import scala.language.reflectiveCalls
import scala.util.{Failure, Success, Random, Try}

/**
 * Created by mateusz on 23.09.15.
 */
abstract class SalatDAOWithCfg[ObjectType <: AnyRef](databaseUri: String, collectionName: String)
                                                    (implicit mot: Manifest[ObjectType], ctx: Context)
  extends SalatDAO[ObjectType, String](
    collection = {
      val clientURI = MongoClientURI(databaseUri)
      val dbName = clientURI.database.getOrElse {
        throw new IllegalArgumentException(s"You must provide database name in connection uri")
      }
      MongoClient(clientURI)(dbName).getCollection(collectionName).asScala
    }) {
  override def defaultWriteConcern = WriteConcern.Acknowledged

  RegisterJodaTimeConversionHelpers()
}

case class BoobsListener(@Key("_id") id: String, chatId: Int, created: DateTime = DateTime.now())

trait BoobsListenerDao {
  def uri: String

  def collection: Option[String] = None

  lazy val dao = new SalatDAOWithCfg[BoobsListener](uri, collection.getOrElse("listeners")) {}
}

case class Files(@Key("_id") fileId: String, typ: String, hash:Array[Byte], random: Double = Random.nextDouble(), created: DateTime = DateTime.now())

object Files {
  val document = "document"
  val photo = "photo"
}

trait FilesDao {
  def uri: String

  def collection: Option[String] = None

  lazy val dao = new SalatDAOWithCfg[Files](uri, collection.getOrElse("filess")) {}

  def random(x: Int) = {
    dao.find(MongoDBObject("random" -> MongoDBObject("$gt" -> Random.nextDouble()))).sort(MongoDBObject("random" -> 1)).take(x)
  }
}

object CyckoBot {

  private[CyckoBot] case class PublishBoobs(f: Files, owner: Int, worker: Option[ActorRef] = None)

  def props() =
    Props(classOf[CyckoBot])
}

class CyckoBot extends Actor with ActorLogging {

  import context.dispatcher

  val hardSelf = self

  val FilesDao = new FilesDao {
    override def uri: String = "mongodb://localhost/CyckoBot"
  }
  val SubsDao = new BoobsListenerDao {
    override def uri: String = "mongodb://localhost/CyckoBot"
  }


  override def receive: Receive = {
    case NewUpdate(id, _, Update(uId, SingleArgCommandMessage("resolve", arg, (chat, _, _)))) =>
      resolveLink(arg, chat.chatId, sender())
    case NewUpdate(id, _, u@Update(_, m)) if m.text.isDefined =>
      val g = m.text.get
      g.stripPrefix("/") match {
        case start if start.startsWith("start") =>
          sendHelp(m.chatId)
        case getImages if getImages.startsWith("get") =>
          val numberStr = g.stripPrefix("get").dropWhile(_ == " ").takeWhile(_ != " ")
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
              val a: Imports.WriteResult = FilesDao.dao.remove(MongoDBObject("_id" -> toRemove))
              sender() ! SendMessage(m.chatId, "Deleted.")
            } else if (m.reply_to_message.get.photo.isDefined) {
              val photos = m.reply_to_message.get.photo.get
              val biggest: String = photos.sortBy(_.width).last.file_id
              val a: Imports.WriteResult = FilesDao.dao.remove(MongoDBObject("_id" -> biggest))
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
      val response = Try {
        val files = Files(biggest.file_id, Files.photo)
        FilesDao.dao.insert(files)
        self.forward(PublishBoobs(files, m.chatId))
        "Thx!"
      }.recover {
        case e: DuplicateKey => "This image is already in DataBase"
        case anot => s"Another error ${anot.getMessage}"
      }.get
      sender() ! HandledUpdate(id, SendMessage(m.chatId, response))

    case NewUpdate(id, _, u@Update(_, m)) if m.document.isDefined =>
      val doc = m.document.get
      val response: String = if (doc.mime_type.isDefined) {
        val mime = doc.mime_type.get
        if (Set("image/jpeg", "image/png", "image/gif") contains mime) {
          Try {
            val files = Files(doc.file_id, Files.document)
            FilesDao.dao.insert(files)
            self.forward(PublishBoobs(files, m.chatId))
            "Thx!"
          }.recover {
            case e: DuplicateKey => "This image is already in DataBase"
            case anot => s"Another error ${anot.getMessage}"
          }.get
        } else {
          s"Invalid ($mime) mime type."
        }
      } else {
        "Mime type not defined, I suck"
      }
      sender() ! HandledUpdate(id, SendMessage(m.chatId, response))

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
        |/get N - sends You N boobs images from database, max 5
        |/get - sends You 5 boobs images from database
        |/random - sends You random boobs from database, enjoy it ( ͡° ͜ʖ͡°)
        |/delete - only as reply comment to another image, deletes it from database
        |/subscribe - subscribes this conversation for boobs news
        |/unsubscribe - unubscribes this conversation from boobs news
        |/help - prints this message
      """.stripMargin
    sender() ! SendMessage(chatId, text, Some("Markdown"))
  }

  def SendBoobsCorrectType(to: Int, fId: Files): Command = {
    if (fId.typ == Files.document) SendDocument(to, Right(fId.fileId)) else SendPhoto(to, Right(fId.fileId))
  }

  def doSthWithNewFile(chatId: Int, worker: ActorRef, files: Files) = {
    Try(FilesDao.dao.insert(files)).map(x => log.info(s"saved to db $files")).getOrElse {
      log.warning(s"not saved $files")
    }
    hardSelf ! PublishBoobs(files, chatId, Some(worker))
  }

  def resolveLink(link: String, chatId: Int, bot: ActorRef): Unit = {
    val pipe = sendReceive
    pipe(Get(link)).onComplete {
      case Success(res) =>
        log.info(s"get result with $res")
        val data = res.entity.data.toByteArray
        val f: File = File.createTempFile("boobs", ".gif")
        val stream = new FileOutputStream(f)
        stream.write(data)
        stream.flush()
        stream.close()
        val contentType = java.nio.file.Files.probeContentType(Paths.get(f.getAbsolutePath))
        if (contentType == "image/gif") {
          bot ! SendMapped(SendDocument(chatId, Left(f)), {
            case Response(true, Left(id), _) =>
              log.info(s"received $id, I know what it is")
              f.delete()
            case Response(true, Right(m: Message), _) if m.document.isDefined =>
              log.info(s"catched new boobs file ${m.document.get.file_id}")
              doSthWithNewFile(chatId, bot, Files(m.document.get.file_id, "document"))
              f.delete()
            case another =>
              log.warning(s"dont know what is this $another")
              f.delete()
          })
        } else {
          bot ! SendMessage(chatId, s"Can't recognize file, only accept image/gif, current is $contentType")
          f.delete()
        }

      case Failure(t) =>
        t.printStackTrace()
    }
  }
}
