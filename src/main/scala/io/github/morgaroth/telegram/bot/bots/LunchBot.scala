package io.github.morgaroth.telegram.bot.bots

import akka.actor.{Actor, ActorLogging}
import io.github.morgaroth.telegram.bot.core.api.models.{Chat, User}
import io.github.morgaroth.telegram.bot.core.api.models.extractors._

/**
 * Created by mateusz on 26.09.15.
 */
class LunchBot extends Actor with ActorLogging {

  val hardSelf = self

  def grantDzikCardTo(userIdentity: String, author: User): Unit = ???

  def sendDzikCardsStatistics(): Unit = ???

  def sendHelp(): Unit = ???

  def sendMenu(): Unit = ???

  def orderMeal(order: List[String], chat: Chat, user: User): Unit = ???

  override def receive: Receive = {
    case SingleArgCommand("dzikakarta", arg, (chat, author, _)) =>
      grantDzikCardTo(arg, author)
    case NoArgCommand("dzikkarty", (chat, _, _)) =>
      sendDzikCardsStatistics()
    case NoArgCommand("help", (chat, _, _)) =>
      sendHelp()
    case NoArgCommand("menu", (chat, _, _)) =>
      sendMenu()
    case MultiArgCommand("order", arg, chatInfo) =>
      orderMeal(arg, chatInfo._1, chatInfo._2)
    case MultiArgCommand("zamawiam", arg, chatInfo) =>
      orderMeal(arg, chatInfo._1, chatInfo._2)
  }
}
