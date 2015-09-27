package io.github.morgaroth.telegram.bot.bots

import akka.actor.{Props, Actor, ActorLogging}
import io.github.morgaroth.telegram.bot.core.api.models.{ReplyKeyboardHide, ReplyKeyboardMarkup, SendMessage}
import io.github.morgaroth.telegram.bot.core.api.models.formats._
import io.github.morgaroth.telegram.bot.core.api.models.extractors.{OnlyTextMessage, TextReply, NoArgCommand}
import io.github.morgaroth.telegram.bot.core.engine.NewUpdate
import io.github.morgaroth.telegram.bot.core.engine.core.BotActor.SendMapped

import scala.language.reflectiveCalls

/**
 * Created by mateusz on 26.09.15.
 */
object TestKeyboardBot {
  def props = Props[TestKeyboardBot]
}


class TestKeyboardBot extends Actor with ActorLogging {
  override def receive: Receive = {
    case NoArgCommand("start", (ch, _, _)) =>
      sender() ! SendMessage(ch.chatId, text = "Hello", reply_markup = ReplyKeyboardMarkup.once(
        keyboard = List(
          List("test1", "test2"),
          List("test4", "test5"),
          List("test8")
        )
      ))
    case NoArgCommand("hid", (ch, _, _)) =>
      sender() ! SendMessage(ch.chatId, "closing keyboard", reply_markup = ReplyKeyboardHide())
    case NoArgCommand("check", (ch, _, _)) =>
      sender() ! SendMessage(ch.chatId, text = "Wybierz danie", reply_markup = ReplyKeyboardMarkup.once(
        keyboard = List(
          List("danie1"),
          List("danie2"),
          List("danie3")
        )
      ))
    case TextReply(OnlyTextMessage(_, repliedText, from, _), text, chInf) =>
      // user responds
      log.info(s"user responds $text")

    case NewUpdate(_, _, m) =>
      log.warning(s"received $m")
  }
}
