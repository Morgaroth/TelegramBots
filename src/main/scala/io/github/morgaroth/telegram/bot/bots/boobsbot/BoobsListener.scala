package io.github.morgaroth.telegram.bot.bots.boobsbot

import com.novus.salat.annotations._
import com.novus.salat.global.ctx
import com.typesafe.config.Config
import io.github.morgaroth.utils.mongodb.salat.MongoDAOJodaSupport
import org.joda.time.DateTime

case class BoobsListener(@Key("_id") id: String, chatId: Int, created: DateTime = DateTime.now())

trait BoobsListenerDao {
  def config: Config

  def collection: Option[String] = None

  lazy val dao = new MongoDAOJodaSupport[BoobsListener](config, collection.getOrElse("listeners"))
}
