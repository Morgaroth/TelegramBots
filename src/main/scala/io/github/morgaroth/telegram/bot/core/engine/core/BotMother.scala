package io.github.morgaroth.telegram.bot.core.engine.core

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import io.github.morgaroth.telegram.bot.core.engine.WebHookSettings
import io.github.morgaroth.telegram.bot.core.engine.caching.CacheActor
import io.github.morgaroth.telegram.bot.core.engine.core.BotMother.RegisterBot
import io.github.morgaroth.telegram.bot.core.engine.pooling.LongPoolingActor
import io.github.morgaroth.telegram.bot.core.engine.webhooks.WebHookManager

/**
 * Created by mateusz on 22.09.15.
 */

object BotMother {

  def props: Props = props(None)

  def props(webHookSettings: Option[WebHookSettings]): Props = Props(classOf[BotMother], webHookSettings)

  case class RegisterBot(botSettings: BotSettings)

  case class BotRegistered(botActor: ActorRef)

}

class BotMother(webhookMaybe: Option[WebHookSettings]) extends Actor with ActorLogging {

  import context._

  lazy val webHookManager = webhookMaybe.map(sett => context.actorOf(WebHookManager.props(sett), "webhook-manager"))

  def receive: Receive = {
    case RegisterBot(sett) =>
      val cacheRef: ActorRef = sett.cacheType match {
        case RAMCache(ret) => actorOf(CacheActor.RAMProps(ret), s"${sett.botName}-ram-cache")
        case MongoCache(ret, uri, colName) => actorOf(CacheActor.DBProps(uri, colName, ret), s"${sett.botName}-db-cache")
      }
      val updatesProvider: ActorRef = sett.updatesType match {
        case WebHook =>
          webHookManager.get
        case LongPool =>
          actorOf(LongPoolingActor.props(sett.botName, sett.botToken), s"${sett.botName}-long-poll")
      }
      val botActor = actorOf(BotActor.props(sett.botName, sett.botToken, cacheRef, updatesProvider, sender()), s"${sett.botName}-bot")
    //      sender() ! BotRegistered(botActor)
  }
}
