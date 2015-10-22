package io.github.morgaroth.telegram.bot.botserver

import java.io.File
import io.github.morgaroth.telegram.bot.bots.boobsbot.BoobsBot
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import akka.actor.Props
import com.typesafe.config.ConfigFactory
import io.github.morgaroth.telegram.bot.bots._


object BotServer extends BotsApp {

  def startBotsFromConfigDyn(bots: List[(String, (BotConfig) => Props)], configFile: File): Unit = {
    val botTokens: Map[String, BotConfig] = loadBotsDefs(configFile)
    log.info(s"parsed bots list: $botTokens")
    val botsToLoad = bots.map {
      case (name, props) => botTokens.get(name).map(x => Some(x -> props(x))).getOrElse {
        log.warning(s"bot $name isn't defined in configuration, will not be started")
        None
      }
    }
    startBots(botsToLoad.flatten)

  }

  def startBotsFromConfig(bots: List[(String, Props)], configFile: File): Unit = {
    val botTokens: Map[String, BotConfig] = loadBotsDefs(configFile)
    log.info(s"parsed bots list: $botTokens")
    val botsToLoad = bots.map {
      case (name, props) => botTokens.get(name).map(x => Some(x -> props)).getOrElse {
        log.warning(s"bot $name isn't defined in configuration, will not be started")
        None
      }
    }
    startBots(botsToLoad.flatten)
  }

  def loadBotsDefs(configFile: File): Map[String, BotConfig] = {
    val botTokens: Map[String, BotConfig] = ConfigFactory.parseFile(configFile).as[List[BotConfig]]("bots").groupBy(_.botName).mapValues(_.head)
    botTokens.mapValues(_.validate())
    botTokens
  }

  def main(args: Array[String]) {
    val bots: List[(String, (BotConfig => Props))] = List(
      "BoobsBot" -> (cfg => {
        BoobsBot.props(cfg.additional.getConfig("database"))
      }),
      "NTDBot" -> (s => NTDBot.props())
    )
    val configFile = new File(args(0))
    println(configFile.getAbsolutePath)
    startBotsFromConfigDyn(bots, configFile)
  }

}
