package io.github.morgaroth.telegram.bot.botserver

import akka.actor.{ActorSystem, Props}
import akka.event.Logging
import com.typesafe.config.{Config, ConfigFactory, ConfigException}
import io.github.morgaroth.telegram.bot.core.engine.WebHookSettings
import io.github.morgaroth.telegram.bot.core.engine.core.BotMother.RegisterBot
import io.github.morgaroth.telegram.bot.core.engine.core.{BotMother, BotSettings, LongPool, RAMCache}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader

import scala.language.postfixOps

/**
 * Created by mateusz on 23.09.15.
 */


import java.io.File
import io.github.morgaroth.telegram.bot.core.engine.core._

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Created by mateusz on 18.09.15.
 */
trait Cache {
  def toCacheType: CacheType
}

case class RAMCacheCfg(retention: FiniteDuration) extends Cache {
  override def toCacheType: CacheType = RAMCache(retention)
}

case class DbCacheCfg(retention: FiniteDuration, uri: String, collection: Option[String]) extends Cache {
  override def toCacheType: CacheType = MongoCache(retention, uri, collection.getOrElse("updates"))
}

case object NoCacheCfg extends Cache {
  override def toCacheType: CacheType = NoCache
}

trait Source {
  def toUpdates: UpdatesSource
}

case class WebHookSource() extends Source {
  def toUpdates = WebHook
}

case class PoolingSource() extends Source {
  def toUpdates = LongPool
}

case class BotSecret(
                      botName: String,
                      botToken: String,
                      ramCache: Option[RAMCacheCfg],
                      dbCache: Option[DbCacheCfg],
                      webhook: Option[WebHookSource],
                      pooling: Option[PoolingSource]
                      ) {
  def validate() = {
    if (ramCache.isDefined && dbCache.isDefined) {
      throw new ConfigException(s"for bot $botName both ramCache and dbCache fields are defined, don't know which choose") {}
    }
    if (webhook.isDefined && pooling.isDefined) {
      throw new ConfigException(s"for bot $botName both webHookUpdates and poolingUpdates fields are defined, don't know which choose") {}
    }
  }

  def cache = ramCache orElse dbCache getOrElse NoCacheCfg

  def updates: Source = webhook orElse pooling getOrElse PoolingSource()
}


trait BotsApp {
  implicit def fileReader = new ValueReader[File] {
    override def read(config: Config, path: String): File = new File(config.getString(path))
  }

  lazy val as = ActorSystem("bots-server")
  lazy val log = Logging(as, getClass)
  lazy val whConfig = {
    val raw = ConfigFactory.load()
    if (raw.hasPath("telegram-api.webhooks")) {
      val cfg = raw.getConfig("telegram-api")
      Some(cfg.as[WebHookSettings]("webhooks"))
    } else None
  }

  lazy val botMother = as.actorOf(BotMother.props(whConfig))

  def startBots(botsToStart: List[(BotSecret, Props)]): Unit = {
    botsToStart.foreach {
      case (c, props) => startBot(c, props)
    }
  }

  def startBot(c: BotSecret, botProps: Props): Unit = {
    val ref = as.actorOf(botProps, s"${c.botName}")
    botMother.tell(RegisterBot(BotSettings(c.botName, c.botToken, c.cache.toCacheType, c.updates.toUpdates)), ref)
  }
}
