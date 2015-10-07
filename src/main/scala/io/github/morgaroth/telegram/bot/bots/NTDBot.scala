package io.github.morgaroth.telegram.bot.bots

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import io.github.morgaroth.telegram.bot.bots.NTDBot.SendBuffer
import io.github.morgaroth.telegram.bot.core.api.models.extractors.NoArgCommand
import io.github.morgaroth.telegram.bot.core.api.models.{ForwardMessage, SendMessage, Update}
import io.github.morgaroth.telegram.bot.core.engine.NewUpdate
import org.joda.time.{DateTime, DateTimeZone, LocalTime}

import scala.collection.mutable
import scala.compat.Platform
import scala.language.{postfixOps, reflectiveCalls}
import scala.concurrent.duration._

/**
 * Created by mateusz on 05.10.15.
 */
object NTDBot {

  case object SendBuffer

  def props() = Props[NTDBot]
}

class NTDBot extends Actor with ActorLogging {

  import context.dispatcher

  val cache = mutable.MutableList.empty[ForwardMessage]

  val mirkoId = -1050746
  //  val mirkoId = -4845793

  var worker: ActorRef = _

  val ntdBegin = new LocalTime(9, 0)
  val ntdEnd = new LocalTime(13, 0)
  val PolandTimeZone = DateTimeZone.forID("Poland")

  def currentInPoland = DateTime.now(PolandTimeZone)

  def currentTimeInPoland = currentInPoland.toLocalTime

  def checkTime(unix: Long) = {
    val t = timeInPoland(unix * 1000)
    t.isAfter(ntdBegin) && t.isBefore(ntdEnd)
  }

  def timeInPoland(millis: Long): LocalTime = {
    new DateTime(millis).toDateTime(PolandTimeZone).toLocalTime
  }

  context.system.scheduler.schedule(0 seconds, 30 seconds, self, SendBuffer)

  override def receive: Receive = {
    case NoArgCommand("start", (ch, _, _)) =>
      log.info(s"start")
      worker = sender()
      worker ! SendMessage(ch.chatId, "NTD bot, wysyłaj mi prywatnie wiadomości, a ja wyślę je po NTD na Mirko (⌐ ͡■ ͜ʖ ͡■)")

    case NoArgCommand("localTime", (Left(ch), _, _)) =>
      log.info(s"message from chat $ch")
      worker = sender()
      sender() ! SendMessage(ch.id, s"aktualnie jest $currentTimeInPoland (z datą: $currentInPoland).")

    case NewUpdate(_, _, Update(_, m)) if checkTime(m.date) && m.chat.isLeft =>
      worker = sender()
      log.info(s"saving $m for resend after ntd")
      cache += ForwardMessage(mirkoId, m.chatId, m.message_id)
      sender() ! SendMessage(m.chatId, "Ok, mam ( ͡° ͜ʖ ͡°)", reply_to_message_id = Some(m.message_id))

    case NewUpdate(_, _, Update(_, m)) if checkTime(m.date) =>
      log.info(s"received $m from group chat")

    case NewUpdate(_, _, Update(_, m)) if m.chat.isLeft =>
      sender() ! SendMessage(m.chatId, "Nie czas NTD, pisz prosto na Mirko ( ͡° ʖ̯ ͡°)")

    case NewUpdate(_, _, Update(_, m)) =>
      log.info(s"received message $m, ignoring")

    case SendBuffer if cache.nonEmpty && currentTimeInPoland.isAfter(ntdEnd) =>
      log.info(s"sending buffer...")
      cache.foreach(worker ! _)
      cache.clear()
  }
}
