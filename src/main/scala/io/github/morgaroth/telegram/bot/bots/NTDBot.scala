package io.github.morgaroth.telegram.bot.bots

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import io.github.morgaroth.telegram.bot.bots.NTDBot.{NoInterestingContent, MessageMaxSize, SendBuffer}
import io.github.morgaroth.telegram.bot.core.api.models.extractors.{SingleArgCommand, NoArgCommand}
import io.github.morgaroth.telegram.bot.core.api.models.{Message, ForwardMessage, SendMessage, Update}
import io.github.morgaroth.telegram.bot.core.engine.NewUpdate
import org.joda.time.{DateTime, DateTimeZone, LocalTime}

import scala.collection.mutable
import scala.compat.Platform
import scala.language.{postfixOps, reflectiveCalls}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
 * Created by mateusz on 05.10.15.
 */
object NTDBot {

  case class MessageMaxSize() extends IllegalArgumentException("widomość ma długość równą maximum dopuszczalnej przez Telegram API. Śmierdzi próbą zepsucia bota.")

  case class NoInterestingContent() extends IllegalArgumentException("Widomość nie zawiera sensownej treści, ani linka żadnego, ani mediów. Tylko rozmawiasz?")

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

  def currentDateInPoland = DateTime.now(PolandTimeZone)

  def currentTimeInPoland = currentDateInPoland.toLocalTime

  def checkTime(unix: Long) = {
    val d = dateInPoland(unix * 1000)
    if (Set(6, 7) contains d.dayOfWeek.get) {
      false
    } else {
      val t = d.toLocalTime
      t.isAfter(ntdBegin) && t.isBefore(ntdEnd)
    }
  }

  def dateInPoland(millis: Long): DateTime = {
    new DateTime(millis).toDateTime(PolandTimeZone)
  }

  context.system.scheduler.schedule(0 seconds, 30 seconds, self, SendBuffer)

  def validateMessage(m: Message): Try[Message] = {
    if (Seq(m.audio, m.video, m.document, m.photo, m.location).flatten.nonEmpty) {
      Success(m)
    } else if (m.text.isDefined) {
      val t = m.text.get
      if (t.length < 4096) {
        Success(m)
      } else {
        Failure(new MessageMaxSize())
      }
    } else {
      Failure(new NoInterestingContent())
    }
  }

  override def receive: Receive = {
    case NewUpdate(_, _, Update(_, m)) if m.chat.isRight =>
      log.info(s"received $m from group chat, ignoring")

    case NoArgCommand("start", (ch, _, _)) =>
      log.info(s"start")
      worker = sender()
      worker ! SendMessage(ch.chatId, "NTD bot, wysyłaj mi prywatnie wiadomości, a ja wyślę je po NTD na Mirko (⌐ ͡■ ͜ʖ ͡■)")

    case NoArgCommand("localTime", (Left(ch), _, _)) =>
      log.info(s"message from chat $ch")
      worker = sender()
      sender() ! SendMessage(ch.id, s"aktualnie jest $currentTimeInPoland (z datą: $currentDateInPoland).")

    case NoArgCommand(any, (Left(user), _, _)) =>
      sender() ! SendMessage(user.id, s"Nie zmam komendy '$any'.")

    case SingleArgCommand(any, _, (Left(user), _, _)) =>
      sender() ! SendMessage(user.id, s"Nie zmam komendy '$any'.")

    case NewUpdate(_, _, Update(_, m)) if checkTime(m.date) =>
      worker = sender()
      val t = validateMessage(m)
      t.foreach { message =>
        log.info(s"saving $m for resend after ntd")
        cache += ForwardMessage(mirkoId, m.chatId, m.message_id)
        sender() ! SendMessage(m.chatId, "Ok, mam ( ͡° ͜ʖ ͡°)", reply_to_message_id = Some(m.message_id))
      }
      t.failed.foreach {
        case t: NoInterestingContent => sender() ! SendMessage(m.chatId, s"Nic ciekawego. ${t.getMessage}", reply_to_message_id = Some(m.message_id))
        case t: MessageMaxSize => sender() ! SendMessage(m.chatId, s"Niepoprawne: ${t.getMessage}", reply_to_message_id = Some(m.message_id))
        case t: Throwable => sender() ! SendMessage(m.chatId, s"INVALID: ${t.getMessage}", reply_to_message_id = Some(m.message_id))
      }

    case NewUpdate(_, _, Update(_, m)) =>
      sender() ! SendMessage(m.chatId, "Nie czas NTD, pisz prosto na Mirko ( ͡° ʖ̯ ͡°)")

    case SendBuffer if cache.nonEmpty && currentTimeInPoland.isAfter(ntdEnd) =>
      log.info(s"sending buffer...")
      cache.foreach(worker ! _)
      cache.clear()
  }
}
