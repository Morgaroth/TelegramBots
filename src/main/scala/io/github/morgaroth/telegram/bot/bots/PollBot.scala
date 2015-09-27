package io.github.morgaroth.telegram.bot.bots

import akka.actor.{Actor, ActorLogging, Props}
import io.github.morgaroth.telegram.bot.core.api.models.extractors.{NoArgCommand, OnlyTextMessage, TextReply}
import io.github.morgaroth.telegram.bot.core.api.models.formats._
import io.github.morgaroth.telegram.bot.core.api.models._
import io.github.morgaroth.telegram.bot.core.engine.NewUpdate

import scala.language.reflectiveCalls

object PollBot {
  def props = Props[PollBot]
}

class PollBot extends Actor with ActorLogging {

  def sendHello(chId: Int) = {
    sender() ! SendMessage(chId,
      """Hello, I'm silent poll bot.""".stripMargin)
  }

  override def receive: Receive = {
    case NoArgCommand("start", (ch, _, _)) =>
      sendHello(ch.chatId)

    case NoArgCommand("newpoll", (Left(user), _, mId)) =>
      sender() ! SendMessage(
        chat_id = user.id,
        text = "Ok! Name your poll",
        reply_to_message_id = Some(mId),
        reply_markup = ForceReply.selective
      )
    case NoArgCommand("newpoll", (Right(group), _, mId)) =>


    case TextReply(m, text, chInf) =>
      // user responds
      log.info(s"user responds $text, replied to $m")
      sender() ! SendMessage(chInf._1.chatId, "tell me answers, one at line", reply_to_message_id = Some(chInf._3), reply_markup = ForceReply.selective)

    case NewUpdate(_, _, m) =>
      log.warning(s"received $m")
  }
}
