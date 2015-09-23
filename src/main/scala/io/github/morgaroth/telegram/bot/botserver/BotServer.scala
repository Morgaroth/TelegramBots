package io.github.morgaroth.telegram.bot.botserver

import java.io.File

import io.github.morgaroth.telegram.bot.bots.TestBot

import scala.language.postfixOps

/**
 * Created by mateusz on 18.09.15.
 */

case class BotSecret(botName: String, botToken: String)

object BotServer extends BotsApp {
  def main(args: Array[String]) {

    val bots = List(
      "testBot" -> TestBot.props
    )
    val configFile = new File(args(0))
    startBots(bots, configFile)
  }

}
