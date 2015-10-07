package io.github.morgaroth.telegram.bot.bots

import java.io.{File => JFile, FileOutputStream}
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
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
import io.github.morgaroth.telegram.bot.core.api.models.extractors.{SingleArgCommand, NoArgCommand, SingleArgCommandMessage}
import io.github.morgaroth.telegram.bot.core.engine.NewUpdate
import io.github.morgaroth.telegram.bot.core.engine.core.BotActor._
import org.joda.time.DateTime
import spray.client.pipelining._
import spray.http.HttpHeaders.`Content-Type`
import spray.http.MediaTypes

import scala.language.reflectiveCalls
import scala.util.{Failure, Random, Success, Try}

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

case class Files(@Key("_id") fileId: String, typ: String, hash: String, random: Double = Random.nextDouble(), created: DateTime = DateTime.now())

object Files {
  val document = "document"
  val photo = "photo"
}

trait FilesDao {
  def uri: String

  def collection: Option[String] = None

  lazy val dao = new SalatDAOWithCfg[Files](uri, collection.getOrElse("files")) {}

  def random(x: Int): List[Files] = {
    List.fill(x) {
      dao.find(MongoDBObject("random" -> MongoDBObject("$gt" -> Random.nextDouble()))).sort(MongoDBObject("random" -> 1)).take(1).toList.headOption
    }.flatten
  }

  def byHash(hash: String): Option[Files] = dao.findOne(MongoDBObject("hash" -> hash))

  def byId(id: String): Option[Files] = dao.findOneById(id)
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
    case SingleArgCommand("resolve", arg, (chat, _, _)) =>
      resolveLink(arg, chat.chatId, sender())

    case SingleArgCommand("add", arg, (chat, _, _)) =>
      resolveLink(arg, chat.chatId, sender(), publish = false)

    case NoArgCommand("stats", (chat, _, _)) =>
      sender() ! SendMessage(chat.chatId,
        s"""Status are:
           |* all files in database = ${FilesDao.dao.count()}
           |* all subscribers in database = ${SubsDao.dao.count()}
           |
          |This is all for now.
      """.stripMargin)

    case NoArgCommand("all", (ch, user, _)) =>
      if (user.id == 36792931) {
        FilesDao.dao.find(MongoDBObject.empty).foreach(f =>
          sender() ! SendBoobsCorrectType(ch.chatId, f)
        )
      } else {
        sender() ! SendMessage(ch.chatId, s"Sorry, I dont know command 'all'.")
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
      val hardSender = sender()
      FilesDao.byId(biggest.file_id) match {
        case Some(prev) =>
          hardSender ! SendMessage(u.message.chatId, "This image is already in DataBase")
        case None =>
          hardSender ! SendMapped(GetFile(biggest.file_id), {
            case Response(_, Right(f@File(_, _, Some(fPath))), _) =>
              hardSender ! FetchFile(f, Files.photo, u.message.chatId)
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
            val files = Files(fId, t, hash)
            FilesDao.dao.insert(files)
            self.forward(PublishBoobs(files, author))
            "Thx!"
          }.recover {
            case e: DuplicateKey => "This image is already in DataBase"
            case anot => s"Another error ${anot.getMessage}"
          }.get
      }
      sender() ! SendMessage(author, info)

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
                  hardSender ! FetchFile(f, Files.document, u.message.chatId)
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

  def SendBoobsCorrectType(to: Int, fId: Files): Command = {
    if (fId.typ == Files.document) SendDocument(to, Right(fId.fileId)) else SendPhoto(to, Right(fId.fileId))
  }

  def doSthWithNewFile(chatId: Int, worker: ActorRef, files: Files, publish: Boolean = true) = {
    Try(FilesDao.dao.insert(files)).map(x => log.info(s"saved to db $files")).getOrElse {
      log.warning(s"not saved $files")
    }
    if (publish) {
      hardSelf ! PublishBoobs(files, chatId, Some(worker))
    }
  }

  def resolveLink(link: String, chatId: Int, bot: ActorRef, publish: Boolean = true): Unit = {
    val pipe = sendReceive
    pipe(Get(link)).onComplete {
      case Success(res) =>
        log.info(s"get result with $res")
        val data = res.entity.data.toByteArray
        val f: JFile = JFile.createTempFile(Random.alphanumeric.take(10).mkString, ".boobs")
        val stream = new FileOutputStream(f)
        stream.write(data)
        stream.flush()
        stream.close()
        val contentType = res.headers.find(_.name == `Content-Type`.name) match {
          case Some(`Content-Type`(types)) if types.mediaType == MediaTypes.`image/gif` =>
            "image/gif"
          case Some(`Content-Type`(types)) => types.mediaType.value
          case None => "unrecognized"
        }
        if (Set("image/gif", "image/jpeg") contains contentType) {
          val hash = calculateMD5(f)
          FilesDao.byHash(hash) match {
            case Some(previous) =>
              bot ! SendMessage(chatId, "This image is already in DataBase")
            case None =>
              bot ! SendMapped(SendDocument(chatId, Left(f)), {
                case Response(true, Left(id), _) =>
                  log.info(s"received $id, I know what it is")
                  f.delete()
                case Response(true, Right(m: Message), _) if m.document.isDefined =>
                  log.info(s"catched new boobs file ${m.document.get.file_id}")
                  doSthWithNewFile(chatId, bot, Files(m.document.get.file_id, Files.document, hash), publish)
                  f.delete()
                case another =>
                  log.warning(s"dont know what is this $another")
                  f.delete()
              })
          }
        } else {
          bot ! SendMessage(chatId, s"Can't recognize file, only accept image/gif, current is $contentType")
          f.delete()
        }

      case Failure(t) =>
        t.printStackTrace()
    }
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
