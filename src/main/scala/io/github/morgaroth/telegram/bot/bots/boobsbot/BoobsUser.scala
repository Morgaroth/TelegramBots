package io.github.morgaroth.telegram.bot.bots.boobsbot

import com.novus.salat.annotations._
import com.novus.salat.global.ctx
import com.typesafe.config.Config
import io.github.morgaroth.telegram.bot.core.api.models.User
import io.github.morgaroth.utils.mongodb.salat.MongoDAOJodaSupport
import org.joda.time.DateTime

case class BoobsUser(@Key("_id") id: String, user: User, created: DateTime = DateTime.now())

trait BoobsUserDao {
  def config: Config

  def collection: Option[String] = None

  lazy val dao = new MongoDAOJodaSupport[BoobsUser](config, collection.getOrElse("users"))
}
