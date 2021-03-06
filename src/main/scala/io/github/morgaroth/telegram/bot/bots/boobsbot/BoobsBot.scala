package io.github.morgaroth.telegram.bot.bots.boobsbot

import java.io.{File => JFile}
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingAdapter
import com.mongodb.DuplicateKeyException
import com.mongodb.MongoException.DuplicateKey
import com.mongodb.casbah.commons.MongoDBObject
import com.typesafe.config.Config
import io.github.morgaroth.telegram.bot.bots.boobsbot.BoobsBot.PublishBoobs
import io.github.morgaroth.telegram.bot.bots.boobsbot.FetchAndCalculateHash.{NoContentInformation, UnsupportedBoobsContent}
import io.github.morgaroth.telegram.bot.core.api.methods.Response
import io.github.morgaroth.telegram.bot.core.api.models._
import io.github.morgaroth.telegram.bot.core.api.models.extractors.{MultiArgCommand, NoArgCommand, SingleArgCommand}
import io.github.morgaroth.telegram.bot.core.api.models.formats._
import io.github.morgaroth.telegram.bot.core.engine.NewUpdate
import io.github.morgaroth.telegram.bot.core.engine.core.BotActor._
import org.bson.types.ObjectId
import org.joda.time.DateTimeZone

import scala.language.reflectiveCalls
import scala.util.{Failure, Success, Try}


object BoobsBot {

  private[BoobsBot] case class PublishBoobs(content: Boobs, owner: Chat, worker: Option[ActorRef] = None)

  def props(cfg: Config) = Props(classOf[BoobsBot], cfg)
}

class BoobsBot(cfg: Config) extends Actor with ActorLogging {

  import context.{dispatcher, system}

  implicit def implLog: LoggingAdapter = log

  val hardSelf = self

  val BoobsDB = new BoobsDao {
    override def config = cfg.getConfig("database")
  }
  val SubsDao = new BoobsListenerDao {
    override def config = cfg.getConfig("database")
  }
  val UsersDao = new BoobsUserDao {
    override def config = cfg.getConfig("database")
  }
  val WaitingLinks = new BoobsInMotionGIFDao {
    override def dbConfig: Config = cfg.getConfig("database")
  }

  val BOT_CREATOR = 36792931

  val BOOBS_SECRETS = Set(
    84564283, // Da
    76500924, // cz
    36792931 // ma
  )

  var knownUsers = UsersDao.dao.find(MongoDBObject.empty).map(_.user.id).toSet

  val questions = scala.collection.mutable.Map.empty[Int, (ObjectId, String, Option[String => Unit])]

  def updateUserDB(user: User) = {
    val result = Try {
      if (knownUsers.contains(user.id)) None else {
        knownUsers += user.id
        UsersDao.dao.insert(BoobsUser(user.id.toString, user))
      }
    }.recoverWith {
      case e: DuplicateKey => Success(None)
      case e: DuplicateKeyException => Success(None)
    }
    result.failed.map(x => log.error(x, s"error during saving user: ${x.getMessage}"))
  }

  override def receive: Receive = {
    case NewUpdate(_, _, update) if {
      updateUserDB(update.message.from)
      false
    } => ???
    case SingleArgCommand("resolve", arg, (chat, _, _)) =>
      resolveLink(arg, chat, sender())

    case SingleArgCommand("add", arg, (chat, _, _)) =>
      resolveLink(arg, chat, sender(), publish = false)

    case NoArgCommand("stats", (chat, _, _)) =>
      sender() ! SendMessage(chat.chatId,
        s"""Statistics are:
           |* all boobs in database (/2)= ${BoobsDB.dao.count()}
           |* all subscribers in database = ${SubsDao.dao.count()}
           |
          |This is all for now.
      """.stripMargin)

    case NoArgCommand("waiting_stats", (chat, _, _)) =>
      sender() ! SendMessage(chat.chatId,
        s"""Statistics are:
           |* waiting = ${WaitingLinks.countWaiting}
           |* accepted = ${WaitingLinks.countAccepted}
           |* rejected = ${WaitingLinks.countRejected}
           |This is all for now.
      """.stripMargin)

    case NoArgCommand("all", (ch, user, _)) if ch.isPrvChat && user.id == BOT_CREATOR =>
      BoobsDB.dao.find(MongoDBObject.empty).foreach(f =>
        sender() ! SendBoobsCorrectType(ch.chatId, f)
      )

    case NoArgCommand("grade", (ch, user, _)) if ch.isPrvChat && BOOBS_SECRETS.contains(user.id) =>
      sender() ! ch.msg(
        """You are in grading process, in loop you receive image and list of possible answers:
          |- *grade_YES* marks boobs as good enough and saves in db
          |- *grade_PUBLISH* as *grade_YES* and sends boobs to subscribents
          |- *grade_NO* condemns those boobs to eternal oblivion
          |- *grade_SKIP* skips image
          |- *stopgrade* ends grading process (clears internal resources)
          |After answer You will get next image. Good luck, boobs lover!
        """.stripMargin, parse_mode = Some("Markdown"))
      sendBoobsToGrade(ch)

    case NoArgCommand("grade", (ch, user, _)) =>
      sender() ! ch.msg("Only in private chat.")

    case NoArgCommand(comm, (ch, user, _)) if comm.startsWith("grade_") && ch.isPrvChat && BOOBS_SECRETS.contains(user.id) =>
      val arg = comm.split("_").toList.tail.head
      log.info(s"received grade $arg command")
      (questions.get(ch.chatId), arg) match {
        case (Some((fileId, telegram_f_id, fn)), "YES") =>
          val a = WaitingLinks.updateStatus(fileId, BoobsInMotionGIF.ACC, Some(user))
          doSthWithNewFile(ch, sender(), Boobs.create(telegram_f_id, Boobs.document, List(a.get.hash), ch.uber), publish = false).map { newId =>
            fn.map(_ (newId))
          }

          sendBoobsToGrade(ch)
        case (Some((fileId, telegram_f_id, fn)), "PUBLISH") =>
          val a = WaitingLinks.updateStatus(fileId, BoobsInMotionGIF.ACC, Some(user))
          doSthWithNewFile(ch, sender(), Boobs.create(telegram_f_id, Boobs.document, List(a.get.hash), ch.uber), publish = true)
          sendBoobsToGrade(ch)
        case (Some((fileId, telegram_f_id, fn)), "NO") =>
          WaitingLinks.updateStatus(fileId, BoobsInMotionGIF.REJECTED, Some(user))
          sendBoobsToGrade(ch)
        case (Some((fileId, telegram_f_id, fn)), "SKIP") =>
          WaitingLinks.skip(fileId, user)
          questions -= ch.chatId
          sendBoobsToGrade(ch)
        case (None, _) =>
          sender() ! SendMessage(ch.chatId, s"Sorry, I've no info about Your grade, plase /grade if you want.")
        case (a, b) =>
          sender() ! SendMessage(ch.chatId, s"Sorry, I dont know $b with cache $a, but I have sth for You:")
          sender() ! sendBoobs(1, ch.chatId)
      }
      questions -= ch.chatId

    case NoArgCommand("stopgrade", (ch, _, _)) if ch.isPrvChat =>
      questions -= ch.chatId
      sender() ! SendMessage(ch.chatId, "OK, end", reply_markup = ReplyKeyboardHide())

    case MultiArgCommand("updatedb", args, (ch, user, _)) if ch.isPrvChat && user.id == BOT_CREATOR =>
      val sen = sender()
      Try {
        val f = args.map(_.toInt) match {
          case from :: to :: Nil => LinksFromTumblrFetch.runAsync(BoobsDB, WaitingLinks, "boobsinmotion", from, Some(to))
          case from :: Nil => LinksFromTumblrFetch.runAsync(BoobsDB, WaitingLinks, "boobsinmotion", from)
          case Nil => LinksFromTumblrFetch.runAsync(BoobsDB, WaitingLinks, "boobsinmotion", 0)
        }
        f.onComplete {
          case Success(inserted) =>
            sen ! SendMessage(ch.chatId, s"fetching BoobsinMotion blog with args $args end with $inserted new links")
          case Failure(t) =>
            sen ! SendMessage(ch.chatId, s"fetching BoobsinMotion blog with args $args end with error: ${t.getMessage}")
            log.error(t, "fetching images")
        }
      }.recover {
        case t =>
          sen ! SendMessage(ch.chatId, s"parsing arguments for command fetching blog end with error ${t.getMessage}")
          log.error(t, "parsing arguments")
      }

    case NoArgCommand("start" | "help", (ch, _, _)) =>
      sendHelp(ch.chatId)

    case NoArgCommand("subscribe", (ch, _, _)) =>
      val response = Try {
        SubsDao.dao.insert(BoobsListener(ch.chatId.toString, ch.chatId))
        "This chat is subscribed!"
      }.recover {
        case e: DuplicateKey => "This chat is already subscribed"
        case anot => s"Another error ${anot.getMessage}"
      }.get
      sender() ! SendMessage(ch.chatId, response)

    case NoArgCommand("boobs" | "make_me_happy" | "give_me_boobs" | "im_not_happy_yet", (ch, _, _)) =>
      sendBoobs(1, ch.chatId)

    case NoArgCommand("unsubscribe", (ch, _, _)) =>
      val wr = SubsDao.dao.remove(MongoDBObject("chatId" -> ch.chatId))
      val response = if (wr.getN == 0) "Unsubscribed (newer was)" else "Unsubscribed :("
      sender() ! SendMessage(ch.chatId, response)


    case NewUpdate(id, _, u@Update(_, m)) if m.text.isDefined =>
      val g = m.text.get
      g.stripPrefix("/") match {
        case delete if delete.startsWith("delete") && BOOBS_SECRETS.contains(m.from.id) =>
          if (m.reply_to_message.isDefined) {
            if (m.reply_to_message.get.document.isDefined) {
              val toRemove = m.reply_to_message.get.document.get.file_id
              BoobsDB.dao.remove(MongoDBObject("_id" -> toRemove))
              sender() ! SendMessage(m.chatId, "Deleted.")
            } else if (m.reply_to_message.get.photo.isDefined) {
              val photos = m.reply_to_message.get.photo.get
              val biggest: String = photos.sortBy(_.width).last.file_id
              BoobsDB.dao.remove(MongoDBObject("_id" -> biggest))
              sender() ! SendMessage(m.chatId, "Deleted.")
            } else {
              sender() ! SendMessage(m.chatId, "Sorry, this message isn't a photo/file comment of reply.")
            }
          } else {
            sender() ! SendMessage(m.chatId, "Sorry, this message isn't a comment of reply.")
          }
        case illegal if m.reply_to_message.isDefined =>
        // ignore, someone only commented Bot message
        case unknown =>
      }
      sender() ! Handled(id)

    // wyłapywanie obrazków
    case NewUpdate(id, _, u@Update(_, m)) if m.photo.isDefined =>
      // catched photo
      val photos = m.photo.get
      val biggest: PhotoSize = photos.sortBy(_.width).last
      val hardSender = sender()
      BoobsDB.byId(biggest.file_id) match {
        case Some(prev) =>
          hardSender ! SendMessage(u.message.chatId, "This image is already in DataBase")
        case None =>
          hardSender ! SendMapped(GetFile(biggest.file_id), {
            case Response(_, Right(f@File(_, _, Some(fPath))), _) =>
              hardSender ! FetchFile(f, data => hardSelf.tell(FileFetchingResult(f, u.message.chat, Boobs.photo, data), hardSender))
              log.info(s"got file $fPath, downloading started")
            case other =>
              log.warning(s"got other response $other")
          })
      }

    case FileFetchingResult(File(fId, _, _), author, t, Success(data)) =>
      log.info(s"file $fId fetched")
      val hash = calculateMD5(data)
      val info = BoobsDB.byHash(hash) match {
        case Some(prev) => "This image is already in DataBase"
        case None =>
          Try {
            val files = Boobs.create(fId, t, List(hash), author.uber)
            BoobsDB.dao.insert(files)
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
        if (Set("image/jpeg", "image/png", "image/gif", "video/mp4") contains mime) {
          val hardSender = sender()
          BoobsDB.byId(doc.file_id) match {
            case Some(prev) =>
              hardSender ! SendMessage(u.message.chatId, "This image is already in DataBase")
            case None =>
              hardSender ! SendMapped(GetFile(doc.file_id), {
                case Response(_, Right(f@File(_, _, Some(fPath))), _) =>
                  hardSender ! FetchFile(f, data => hardSelf.tell(FileFetchingResult(f, u.message.chat, Boobs.document, data), hardSender))
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

  def sendBoobs(x: Int, ch: Int): Unit = {
    BoobsDB.random(x).foreach(fId =>
      sender() ! SendBoobsCorrectType(ch, fId)
    )
  }

  def sendBoobsToGrade(ch: Chat, respondTo: Option[ActorRef] = None): Unit = {
    val toSet = questions.values.map(_._1).toSet
    log.info(s"used boobs $toSet")
    val toMaybe = WaitingLinks.oneWaiting(toSet, ch.prv)
    log.info(s"next image will be $toMaybe")
    toMaybe.map { to =>
      questions += ch.chatId -> (to._id.get, null, None)
      val req = respondTo.getOrElse(sender())
      log.info(s"using ${to.link}")
      FetchAndCalculateHash(to.link).map { case (hash, file, contentType) =>
        val hash1 = BoobsDB.byHash(hash)
        if (hash1.nonEmpty) {
          req ! SendMapped(SendMessage(ch.chatId,
            s"""Suprisingly this image is in DB already, looking for next...
               |inserted by ${hash1.get.creator.map(_.getAnyUserName).getOrElse("anonymous")} at ${hash1.get.created.withZone(DateTimeZone.forID("Poland")).toString}
               |image link ${to.link}
               |previously/already calculated hashes:
               |${to.hash}
               |$hash""".stripMargin), {
            case Response(true, Right(m: Message), _) =>
              req ! SendDocument(ch.chatId, Right(hash1.get.fileId), reply_to_message_id = Some(m.message_id))
            case a =>
              req ! SendMessage(ch.chatId, s"unknown respond ${a.toString}")
          })
          WaitingLinks.updateStatus(to._id.get, BoobsInMotionGIF.DUPLICATED, None)
          file.delete()
          questions -= ch.chatId
          sendBoobsToGrade(ch, Some(req))
        } else {
          req ! SendMapped(SendDocument(ch.chatId, Left(file)), {
            case Response(true, Left(id), _) =>
              log.info(s"received $id, I know what it is")
              questions -= ch.chatId
              file.delete()
            case Response(true, Right(m: Message), _) if m.document.isDefined =>
              val telegram_file_id = m.document.get.file_id
              log.info(s"catched new boobs file $telegram_file_id")
              req ! SendMapped(GetFile(telegram_file_id), {
                case Response(_, Right(f@File(_, _, Some(fPath))), _) =>
                  req ! FetchFile(f, data => {
                    val h = calculateMD5(data.get)
                    if (BoobsDB.byHash(h).isEmpty) {
                      questions += ch.chatId -> (to._id.get, telegram_file_id, None)
                      req ! SendMessage(ch.chatId, s"Grade, /grade_YES /grade_PUBLISH /grade_NO\n\n/stopgrade /grade_SKIP\nif end.", reply_to_message_id = Some(m.message_id))
                      file.delete()
                    } else {
                      req ! SendMessage(ch.chatId, s"Suprisingly image is in DB already, looking for next")
                      WaitingLinks.updateStatus(to._id.get, BoobsInMotionGIF.DUPLICATED, None)
                      questions -= ch.chatId
                      sendBoobsToGrade(ch, Some(req))
                    }
                    if (h != to.hash) {
                      def fn(dbId: String): Unit = {
                        BoobsDB.appendHashById(dbId, h)
                      }

                      questions += ch.chatId -> (to._id.get, telegram_file_id, Some(fn _))
                      log.warning(s"hashes aren't the same = $h ${to.hash}")
                    }
                  })
                  log.info(s"got file $fPath, downloading started")
                case other =>
                  log.warning(s"got other response $other")
              })
            case another =>
              log.warning(s"don't know what is this $another")
              questions -= ch.chatId
              file.delete()
          })
        }
      }
    }.getOrElse {
      sender() ! SendMessage(ch.chatId, "No waiting links")
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
        |/boobs - Booooobs!
        |/give_me_boobs - Booooobs!
        |/make_me_happy - Booooobs!
        |/resolve - downloads gif from http link to gif, publishes new file to all subscribers
        |/add - like resolve, but without publishing
        |/stats - prints some DB statistics
        |/subscribe - subscribes this chat to boobs news
        |/unsubscribe - unsubscribes
        |/waiting_stats - sends statistics about db of waiting images
        |/delete - as reply to image - deletes boobs from DB
        |/help - returns this help""".stripMargin + (
        if (chatId == BOT_CREATOR)
          """
            |/all - sends all boobs
            | """.stripMargin
        else "") + (
        if (BOOBS_SECRETS.contains(chatId))
          """
            |/grade - start grading waiting images
            | """.stripMargin
        else "")
    sender() ! SendMessage(chatId, text)
  }

  def SendBoobsCorrectType(to: Int, fId: Boobs): Command = {
    if (fId.typ == Boobs.document) SendDocument(to, Right(fId.fileId)) else SendPhoto(to, Right(fId.fileId))
  }

  def doSthWithNewFile(chatId: Chat, worker: ActorRef, files: Boobs, publish: Boolean = true): Option[String] = {
    val result = Try(BoobsDB.dao.insert(files))
    result.map(x => log.info(s"saved to db $files")).getOrElse {
      log.warning(s"not saved $files")
    }
    if (publish) {
      hardSelf ! PublishBoobs(files, chatId, Some(worker))
    }
    result.toOption.flatten
  }

  def resolveLink(link: String, user: Chat, bot: ActorRef, publish: Boolean = true): Unit = {
    FetchAndCalculateHash(link).map { case (hash, tmpFile, contentType) =>
      BoobsDB.byHash(hash) match {
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
              doSthWithNewFile(user, bot, Boobs.create(m.document.get.file_id, Boobs.document, List(hash), user.uber, Map("telegram_conent_type" -> contentType)), publish)
              tmpFile.delete()
            case another =>
              log.warning(s"don't know what is this $another")
              tmpFile.delete()
          })
      }
    }
  }.recover {
    case UnsupportedBoobsContent(other) =>
      bot ! SendMessage(user.chatId, s"Can't recognize file, current is ${other.value}")
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

  log.info("BoobsBot STARTED!")
}
