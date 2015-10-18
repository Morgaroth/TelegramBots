package io.github.morgaroth.telegram.bot.bots.boobsbot

import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.annotations._
import com.novus.salat.global.ctx
import com.typesafe.config.Config
import io.github.morgaroth.telegram.bot.core.api.models.UberUser
import io.github.morgaroth.utils.mongodb.salat.MongoDAO
import org.joda.time.DateTime

import scala.util.Random

case class Boobs(
                  @Key("_id") fileId: String,
                  typ: String,
                  hash: String,
                  creator: Option[UberUser] = None,
                  random: Double = Random.nextDouble(),
                  created: DateTime = DateTime.now()
                  )

object Boobs {
  val document = "document"
  val photo = "photo"
}

trait BoobsDao {
  def config: Config

  def collection: Option[String] = None

  lazy val dao = {
    val d = new MongoDAO[Boobs](config, collection.getOrElse("files")) {}
    d.collection.ensureIndex("hash")
    d
  }

  def random(x: Int): List[Boobs] = {
    if (dao.count() != 0) {
      Stream.continually(
        dao
          .find(MongoDBObject("random" -> MongoDBObject("$gt" -> Random.nextDouble())))
          .sort(MongoDBObject("random" -> 1))
          .take(1)
          .toList.headOption
      ).flatten.take(x).toList
    } else List.empty
  }

  def byHash(hash: String): Option[Boobs] = dao.findOne(MongoDBObject("hash" -> hash))

  def byId(id: String): Option[Boobs] = dao.findOneById(id)

  def contains(hash:String) = dao.findOne(MongoDBObject("hash" -> hash)).nonEmpty
}
