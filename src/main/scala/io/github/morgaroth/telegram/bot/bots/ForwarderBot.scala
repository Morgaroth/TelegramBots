package io.github.morgaroth.telegram.bot.bots

import java.io.{File, FileOutputStream}
import java.nio.file.Paths

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import io.github.morgaroth.telegram.bot.core.api.methods.Response
import io.github.morgaroth.telegram.bot.core.api.models.{ForwardMessage, Message, SendDocument, SendMessage}
import io.github.morgaroth.telegram.bot.core.engine.NewUpdate
import io.github.morgaroth.telegram.bot.core.engine.core.BotActor.{HandledUpdate, Initialized, SendMapped}
import spray.client.pipelining._

import scala.util.{Failure, Success}

/**
 * Created by mateusz on 23.09.15.
 */
object ForwarderBot {
  def props = Props(classOf[ForwarderBot])
}


class ForwarderBot extends Actor with ActorLogging {

  import context.dispatcher

  override def receive: Receive = {
    case Initialized =>
      log.info("bot actor initialized")
    case u@NewUpdate(_, _, update) =>
      log.info(s"received update ${u.update.update_id}")
      sender() ! HandledUpdate(u, ForwardMessage(update.message.chatId, update.message.chatId, update.message.message_id))
  }
}
