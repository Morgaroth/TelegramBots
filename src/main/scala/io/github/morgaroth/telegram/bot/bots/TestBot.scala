package io.github.morgaroth.telegram.bot.bots

import akka.actor.{Actor, ActorLogging, Props}

/**
 * Created by mateusz on 23.09.15.
 */
object TestBot {
  def props = Props(classOf[TestBot])
}


class TestBot extends Actor with ActorLogging {
  override def receive: Receive = {
    case u =>
      log.info("received ")
  }
}
