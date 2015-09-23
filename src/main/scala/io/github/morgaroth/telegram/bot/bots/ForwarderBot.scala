package io.github.morgaroth.telegram.bot.bots

import akka.actor.{Actor, ActorLogging, Props}
import io.github.morgaroth.telegram.bot.core.api.models.ForwardMessage
import io.github.morgaroth.telegram.bot.core.engine.NewUpdate
import io.github.morgaroth.telegram.bot.core.engine.core.BotActor.{HandledUpdate, Initialized}

/**
 * Created by mateusz on 23.09.15.
 */
object ForwarderBot {
  def props = Props(classOf[ForwarderBot])
}


class ForwarderBot extends Actor with ActorLogging {
  override def receive: Receive = {
    case Initialized =>
      log.info("bot actor initialized")
    case u@NewUpdate(_, _, update) =>
      log.info(s"received update ${u.update.update_id}")
      val chatId = update.message.chatId
      sender() ! HandledUpdate(u, ForwardMessage(chatId, chatId, update.message.message_id))
  }
}
