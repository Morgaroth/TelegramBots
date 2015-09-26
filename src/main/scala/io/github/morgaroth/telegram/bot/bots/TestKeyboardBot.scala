package io.github.morgaroth.telegram.bot.bots

import akka.actor.{Props, Actor, ActorLogging}
import io.github.morgaroth.telegram.bot.core.api.models.{ReplyKeyboardHide, ReplyKeyboardMarkup, SendMessage}
import io.github.morgaroth.telegram.bot.core.api.models.formats._
import io.github.morgaroth.telegram.bot.core.api.models.extractors.NoArgCommandUpdate
import io.github.morgaroth.telegram.bot.core.engine.NewUpdate

import scala.language.reflectiveCalls

/**
 * Created by mateusz on 26.09.15.
 */
object TestKeyboardBot {
  def props = Props[TestKeyboardBot]
}


class TestKeyboardBot extends Actor with ActorLogging {
  override def receive: Receive = {
    case NoArgCommandUpdate("start", (ch, _, _)) =>
      sender() ! SendMessage(ch.chatId, text = "Hello", reply_markup = ReplyKeyboardMarkup.once(
        keyboard = List(
          List("test1", "test2"),
          List("test4", "test5"),
          List("test8", "test9")
        )
      ))
    case NoArgCommandUpdate("hid", (ch, _, _)) =>
      sender() ! SendMessage(ch.chatId, "closing keyboard", reply_markup = ReplyKeyboardHide())
    case NewUpdate(_, _, m) =>
      log.warning(s"received $m")
  }
}
