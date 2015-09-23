package io.github.morgaroth.telegram.bot.botserver

import java.io.File

import akka.actor.{ActorSystem, Props}
import akka.event.Logging
import com.typesafe.config.ConfigFactory
import io.github.morgaroth.telegram.bot.core.engine.core.BotMother.RegisterBot
import io.github.morgaroth.telegram.bot.core.engine.core.{BotMother, BotSettings, LongPool, RAMCache}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Created by mateusz on 23.09.15.
 */
trait BotsApp {
  this: Object =>

  def startBots(bots: List[(String, Props)], configFile: File): Unit = {
    val as = ActorSystem("bots-server")
    val log = Logging(as, getClass)
    val botMother = as.actorOf(BotMother.props(None))

    val botTokens = ConfigFactory.parseFile(configFile).as[List[BotSecret]]("bots").groupBy(_.botName).mapValues(_.head)

    val botsToLoad = bots.map {
      case (name, props) => botTokens.get(name).map(x => Some(x -> props)).getOrElse {
        log.warning(s"bot $name isn't defined in configuration, will not be started")
        None
      }
    }

    botsToLoad.flatten.foreach {
      case (BotSecret(name, token), props) =>
        val ref = as.actorOf(props, s"$name")
        botMother.tell(RegisterBot(BotSettings(name, token, RAMCache(1 day), LongPool)), ref)
    }
  }
}
