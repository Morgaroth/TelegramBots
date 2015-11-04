package io.github.morgaroth.telegram.bot.bots.boobsbot

import akka.actor.ActorSystem
import akka.event.{LoggingAdapter, Logging}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import com.mongodb.MongoException.DuplicateKey
import com.mongodb.{WriteResult, MongoCredential}
import com.mongodb.casbah.Imports
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.global.ctx
import com.tumblr.jumblr.JumblrClient
import com.tumblr.jumblr.types.{PhotoPost, Post}
import com.typesafe.config.{Config, ConfigFactory}
import io.github.morgaroth.telegram.bot.bots.boobsbot.FetchAndCalculateHash.{UnsupportedBoobsContent, NoContentInformation}
import io.github.morgaroth.utils.mongodb.salat._
import org.bson.types.ObjectId
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import spray.httpx.unmarshalling.UnsupportedContentType

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try


/**
  * Created by mateusz on 16.10.15.
  */
object BoobsInMotionGIF {
  val ACC = "accepted"
  val REJECTED = "rejected"
  val WAITING = "waiting"
  val DUPLICATED = "duplicated"

  def waiting(link: String, origin: String, hash: String, postTime: DateTime) = apply(None, link, origin, hash, postTime, WAITING, DateTime.now())
}


case class BoobsInMotionGIF(
                             _id: Option[ObjectId] = None,
                             link: String,
                             origin: String,
                             hash: String,
                             postTime: DateTime,
                             accepted: String,
                             createdAt: DateTime
                           )

trait BoobsInMotionGIFDao {
  def dbConfig: Config


  lazy val dao = {
    val d = new MongoDAOObjectIdKey[BoobsInMotionGIF](dbConfig, "BoobsLinks") with JodaSupport
    d.collection.ensureIndex(MongoDBObject("hash" -> 1), "hash_idx", unique = true)
    d.collection.ensureIndex(MongoDBObject("accepted" -> 1), "status_idx")
    d
  }

  def notContainslink(obj: BoobsInMotionGIF): Boolean = notContainslink(obj.link)

  def notContainslink(link: String): Boolean = dao.findOne(MongoDBObject("link" -> link)).isEmpty

  def findLastInsertedTime =
    dao
      .find(MongoDBObject.empty)
      .sort(MongoDBObject("postTime" -> -1))
      .take(1)
      .toList
      .headOption
      .map(_.postTime)
      .getOrElse(DateTime.now.withMillis(0))

  def oneWaiting(butNo: Set[ObjectId]) = dao.findOne(MongoDBObject(
    "accepted" -> BoobsInMotionGIF.WAITING,
    "_id" -> MongoDBObject("$nin" -> butNo.map(_.toString))
  ))

  def nWaiting(n: Int) = dao.find(MongoDBObject("accepted" -> BoobsInMotionGIF.WAITING)).limit(n).toList

  def updateStatus(id: ObjectId, status: String): Option[BoobsInMotionGIF] = {
    dao.update(MongoDBObject("_id" -> id), MongoDBObject("$set" -> MongoDBObject("accepted" -> status)))
    dao.findOneById(id)
  }

  def updateStatus(id: String, status: String): Option[BoobsInMotionGIF] = updateStatus(new ObjectId(id), status)
}

/**
  * Created by mateusz on 15.10.15.
  */
object LinksFromTumblrFetch extends TumblrKeys {
  val df = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss 'GMT'").withZone(DateTimeZone.forID("Etc/GMT"))

  val tumblrClient = {
    val a = new JumblrClient(KEY, SECRET)
    a.setToken(OAUTH_TOKEN, OAUTH_KEY)
    a
  }

  def doLogic(existingBoobs: BoobsDao, waitingBoobs: BoobsInMotionGIFDao, blog: String, pageStart: Int, pageEnd: Option[Int] = None)(implicit system: ActorSystem, log: LoggingAdapter) = {

    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val b = new Iterator[List[Post]] {
      var current = pageStart

      val info = tumblrClient.blogInfo(s"$blog.tumblr.com")
      val count1 = info.getPostCount
      val allPosts = count1.intValue()

      def blogPosts = tumblrClient.blogPosts(s"$blog.tumblr.com", Map("limit" -> 20, "offset" -> current * 20).asJava)

      val stopTime = waitingBoobs.findLastInsertedTime

      var ifStop = false

      log.info(s"all posts in blog: $allPosts")
      val end = pageEnd.getOrElse(allPosts / 20)

      override def hasNext: Boolean = {
        val r = current < end && (current * 20 < allPosts) && !ifStop
        println(s"has next = $r (current $current, end $end, current*10 ${current * 20}, all $allPosts, ifStop $ifStop)")
        r
      }

      override def next(): List[Post] = {
        println("next")
        val raw = blogPosts.asScala.toList
        val r = raw.filter(x => DateTime.parse(x.getDateGMT, df) isAfter stopTime)
        if (r.size != raw.size) {
          // some removed
          ifStop = true
        }
        current += 1
        r
      }
    }

    def fileMayBeNew(t: BoobsInMotionGIF) = {
      !existingBoobs.contains(t.hash)
    }

    def saveBoobsLink(x: BoobsInMotionGIF): Either[BoobsInMotionGIF, String] = {
      Try {
        val wr = waitingBoobs.dao.insert(x)
        wr.map(waitingBoobs.dao.findOneById).map(_.map(Left(_)).getOrElse(Right("not found after insert"))).getOrElse(Right("not inserted"))
      }.recover {
        case t: DuplicateKey =>
          Right("duplicated hash, loooz")
        case t: Throwable =>
          Right(s"error ${t.getMessage}")
      }.get
    }

    val r = Source(() => b).mapConcat(x => x)
      .filter(_.getType == "photo")
      .map(_.asInstanceOf[PhotoPost])
      .mapConcat(post =>
        post.getPhotos.asScala.toList.map(photo =>
          BoobsInMotionGIF.waiting(photo.getOriginalSize.getUrl, post.getPostUrl, "", DateTime.parse(post.getDateGMT, df))
        )
      )
      .filter(waitingBoobs.notContainslink)
      .mapAsync(10)(x =>
        FetchAndCalculateHash(x.link).map(hashAndFile => Some(x.copy(hash = hashAndFile._1))).recover {
          case UnsupportedBoobsContent(ct) =>
            log.warning(s"file ${x.link} has unsupported content type $ct")
            None
          case NoContentInformation =>
            log.warning(s"file ${x.link} has no content type information")
            None
        }
      )
      .mapConcat(x => x.toList)
      .filter(fileMayBeNew)
      .map(saveBoobsLink)
      .runFold(0) {
        case (acc, x) =>
          println(x)
          acc + x.fold(_ => 1, _ => 0)
      }
    r
  }

  def run(boobs: BoobsDao, waitingBoobs: BoobsInMotionGIFDao, blog: String, pageStart: Int, pageEnd: Option[Int] = None)(implicit system: ActorSystem, log: LoggingAdapter) = {
    Await.result(doLogic(boobs, waitingBoobs, blog, pageStart, pageEnd), 10.minutes)
  }

  def runAsync(boobs: BoobsDao, waitingBoobs: BoobsInMotionGIFDao, blog: String, pageStart: Int, pageEnd: Option[Int] = None)(implicit system: ActorSystem, log: LoggingAdapter) = {
    doLogic(boobs, waitingBoobs, blog, pageStart, pageEnd)
  }


  def main(args: Array[String]) {
    implicit val system = ActorSystem("tumblr")
    implicit val log = Logging(system, getClass)
    val waitingBoobs = new BoobsInMotionGIFDao {
      override def dbConfig: Config = ConfigFactory.parseString( """uri = "mongodb://localhost/TumblrLinks" """)
    }
    val boobs = new BoobsDao {
      override def config: Config = ConfigFactory.parseString( """uri = "mongodb://localhost/CyckoBot" """)
    }

    run(boobs, waitingBoobs, "boobsinmotion", 0, Some(570))
    system.shutdown()
  }
}