package io.github.morgaroth.telegram.bot.bots.boobsbot

import com.mongodb.casbah.BaseImports
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.annotations._
import com.novus.salat.global.ctx
import com.typesafe.config.Config
import io.github.morgaroth.telegram.bot.core.api.models.UberUser
import io.github.morgaroth.utils.mongodb.salat.MongoDAOJodaSupport
import org.joda.time.DateTime

import scala.util.Random

case class Boobs(
                  @Key("_id") fileId: String,
                  typ: String,
                  hashes: List[String],
                  creator: Option[UberUser],
                  random: Double,
                  lastSeen: DateTime,
                  created: DateTime,
                  additional: Map[String, String] = Map.empty
                )

object Boobs {
  val document = "document"
  val photo = "photo"

  def create(fileId: String, typ: String, hashes: List[String], creator: UberUser = null, additional: Map[String, String] = Map.empty) = {
    val now = DateTime.now()
    apply(fileId, typ, hashes, Option(creator), Random.nextDouble(), now, now, additional)
  }

}

trait BoobsDao {
  def config: Config

  def collection: Option[String] = None

  lazy val dao = {
    val d = new MongoDAOJodaSupport[Boobs](config, collection.getOrElse("Boobs")) {}
    d.collection.ensureIndex(MongoDBObject("hashes" -> 1), "hash_idx", unique = true)
    d.collection.ensureIndex(MongoDBObject("lastSeen" -> 1), "last_seen_idx")
    d
  }

  def random(x: Int): List[Boobs] = {
    if (dao.count() != 0) {
      Stream.continually(oneOfFarSeenBoobs).flatten.take(x).toList
    } else List.empty
  }

  def oneRandomBoobs: Option[Boobs] = {
    dao
      .find(MongoDBObject("random" -> MongoDBObject("$gt" -> Random.nextDouble())))
      .sort(MongoDBObject("random" -> 1))
      .take(1)
      .toList.headOption
  }

  def oneOfFarSeenBoobs: Option[Boobs] = {
    Random.shuffle(
      dao
        .find(MongoDBObject.empty)
        .sort(MongoDBObject("lastSeen" -> 1))
        .take(100)
        .toList
    ).headOption.map { boobs =>
      val x = boobs.copy(lastSeen = DateTime.now())
      dao.save(x)
      x
    }
  }

  def byHash(hash: String): Option[Boobs] = dao.findOne(MongoDBObject("hashes" -> hash))

  def byId(id: String): Option[Boobs] = dao.findOneById(id)

  def contains(hash: String) = dao.findOne(MongoDBObject("hashes" -> hash)).nonEmpty

  def appendHashById(id: String, hash: String) = dao.update(MongoDBObject("_id" -> id), MongoDBObject("$addToSet" -> MongoDBObject("hashes" -> hash)))

  def appendHashByHash(hash: String, nextHash: String) = dao.update(MongoDBObject("hashes" -> hash), MongoDBObject("$addToSet" -> MongoDBObject("hashes" -> nextHash)))
}
