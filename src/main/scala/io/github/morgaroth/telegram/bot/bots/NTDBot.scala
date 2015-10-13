package io.github.morgaroth.telegram.bot.bots

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import io.github.morgaroth.telegram.bot.bots.NTDBot.{BanReminder, MessageMaxSize, NoInterestingContent, SendBuffer}
import io.github.morgaroth.telegram.bot.core.api.models._
import io.github.morgaroth.telegram.bot.core.api.models.extractors._
import io.github.morgaroth.telegram.bot.core.engine.NewUpdate
import org.joda.time.{DateTime, DateTimeZone, LocalTime}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.{postfixOps, reflectiveCalls}
import scala.util.{Failure, Success, Try}

object NTDBot {

  case class MessageMaxSize() extends IllegalArgumentException("widomość ma długość równą maximum dopuszczalnej przez Telegram API. Śmierdzi próbą zepsucia bota.")

  case class NoInterestingContent() extends IllegalArgumentException("Widomość nie zawiera sensownej treści, ani linka żadnego, ani mediów. Tylko rozmawiasz?")

  case object SendBuffer

  case object BanReminder

  def props() = Props[NTDBot]
}

class NTDBot extends Actor with ActorLogging {

  import context.dispatcher

  val cache = mutable.MutableList.empty[ForwardMessage]
  val bans = scala.collection.mutable.Map.empty[User, User]

  val mirkoId = -1050746
  //  val mirkoId = -4845793

  var worker: ActorRef = _

  val ntdBegin = new LocalTime(9, 0)
  val ntdEnd = new LocalTime(13, 0)
  val banEnd = new LocalTime(17, 0)
  val PolandTimeZone = DateTimeZone.forID("Poland")

  def currentDateInPoland = DateTime.now(PolandTimeZone)

  def currentTimeInPoland = currentDateInPoland.toLocalTime

  def isNTD(unix: Long) = {
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
  context.system.scheduler.schedule(0 seconds, 10 minutes, self, BanReminder)

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
    case NewChatParticipant(user, (chat, from, _)) =>
      log.info(s"new chat participant $user to $chat added by $from")
      if (chat.chatId == mirkoId) {
        bans -= user
      } else {
        log.warning("WTF?")
      }

    case RemovedParticipant(user, date, (chat, from, _)) if isNTD(date) =>
      log.info(s"removed chat participant $user from $chat by $from, probably banned")
      if (chat.chatId == mirkoId) {
        bans += user -> from
      } else {
        log.warning("WTF?")
      }

    case SingleArgCommand(comm, user, (chat, from, _)) =>
      log.info(s"marking user $user as fired by $from")
      bans.filterNot(_._1.username.contains(user))

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

    case NewUpdate(_, _, Update(_, m)) if isNTD(m.date) =>
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

    case BanReminder if bans.nonEmpty && (currentTimeInPoland.isAfter(banEnd) || currentTimeInPoland.isBefore(ntdBegin)) =>
      val bannedUsers = bans.toList.groupBy(_._2).mapValues(_.map(_._1))
      val banss = bannedUsers.map {
        case (punisher, banned) => s"@${punisher.getAnyUserName}, może powinieneś przywrócić ${banned.map(_.getAnyUserName).mkString(", ")} do Mirko?"
      }
      val (word, word1) = if (bans.size > 2) ("ich", "odpokutowali") else if (bans.head._1.first_name.endsWith("a")) ("ją", "odpokutowała") else ("go", "odpokutował")
      val information = banss.mkString("Przypomnienie o banach!", "\n", s"\nPrzywróćcie $word, już " + word1 + "!\n( ͡° ʖ̯ ͡°)")
      worker ! SendMessage(mirkoId, information)
  }
}
