package io.github.morgaroth.telegram.bot.test

import akka.actor.ActorSystem
import io.github.morgaroth.telegram.bot.api.base.LongPoolingActor

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Created by mateusz on 18.09.15.
 */
object TestApp {
  def main(args: Array[String]) {
    val as = ActorSystem("test-bot")
    import as.dispatcher
    as.actorOf(LongPoolingActor.props(args(0)))
    as.scheduler.scheduleOnce(10 seconds) {
      as.shutdown()
    }
  }
}
