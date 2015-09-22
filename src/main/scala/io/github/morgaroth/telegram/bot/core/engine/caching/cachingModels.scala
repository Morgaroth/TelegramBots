package io.github.morgaroth.telegram.bot.core.engine.caching

import java.util.UUID
import com.novus.salat.global.ctx
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.annotations._
import com.typesafe.config.Config
import io.github.morgaroth.telegram.bot.core.engine.NewUpdate
import io.github.morgaroth.utils.mongodb.salat.MongoDAOStringKey
import org.joda.time.DateTime

/**
 * Created by mateusz on 22.09.15.
 */
case class UpdateRecord(update: NewUpdate, inserted: DateTime = DateTime.now())

case class NewUpdateDB(@Key("_id") id: String, update: NewUpdate, insertedAt: DateTime = DateTime.now())

trait NewUpdateDBBaseDao {

  import net.ceedubs.ficus.Ficus._

  def cfg: Config

  val dao = new MongoDAOStringKey[NewUpdateDB](cfg, cfg.as[Option[String]]("name").getOrElse("updates")) {}

  def save(update: NewUpdate) = {
    dao.save(NewUpdateDB(update.id.toString, update))
  }

  def remove(id: UUID): Unit = {
    dao.removeById(id.toString)
  }

  def getRemaining(botId: String) = {
    dao.find(MongoDBObject("update.botId" -> botId)).toList.map(_.update)
  }

  def dropOld(olderThan: DateTime) = {
    dao.remove(MongoDBObject("insertedAt" -> MongoDBObject("$lte" -> olderThan)))
  }
}