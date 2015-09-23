package io.github.morgaroth.telegram.bot.core.engine.core

import java.util.UUID

import akka.actor._
import io.github.morgaroth.telegram.bot.core.api.methods.{Methods, Response}
import io.github.morgaroth.telegram.bot.core.api.models._
import io.github.morgaroth.telegram.bot.core.engine._
import io.github.morgaroth.telegram.bot.core.engine.core.BotActor.{Handled, HandledUpdate, InitializationFailed, Initialized}

import scala.concurrent.Future
import scala.language.{implicitConversions, reflectiveCalls}
import scala.util.{Failure, Success}

/**
 * Created by mateusz on 23.09.15.
 */
object BotActor {
  def props(botName: String, botToken: String, cacheActor: ActorRef, updatesActor: ActorRef, worker: ActorRef): Props =
    Props(classOf[BotActor], botName, botToken, cacheActor, updatesActor, worker)

  case object GetState

  case class Handled(id: UUID)

  case class HandledUpdate(u: NewUpdate, response: Option[Command])

  trait State

  case object Initialized extends State

  case class InitializationFailed(reason: Either[Response[Boolean], Throwable]) extends State

}

class BotActor(botName: String, val botToken: String, cacheActor: ActorRef, updatesActor: ActorRef, worker: ActorRef)
  extends Actor with ActorLogging with Methods {

  var me: User = null
  getMe().onSuccess { case r =>
    me = r.result.right.get
  }

  updatesActor ! Register(botName, botToken, self)

  def initializing: Receive = {
    case Registered =>
      log.info("registered successfully for updates")
      cacheActor ! GetRemaining(botName)

    case RegisteringFailed(reason) =>
      worker ! InitializationFailed(reason)
      // todo stop updates actor?
      // todo stop cache actor?
      context stop self

    case Remaining(remainingUpdates) =>
      context become working
      worker ! Initialized
      remainingUpdates.sortBy(_.update.update_id).foreach(worker ! _)
  }

  def working: Receive = {
    case u: NewUpdate =>
      log.debug(s"forwarding update ${u.id}")
      cacheActor ! CacheUpdate(u)
      worker ! u

    case Handled(id) =>
      log.debug(s"update $id marked as handled")
      cacheActor ! UpdateHandled(id)

    case h: UpdateHandled =>
      log.debug(s"update ${h.id} marked as handled")
      cacheActor ! h

    case HandledUpdate(update, None) =>
      self ! Handled(update.id)

    case HandledUpdate(update, Some(response)) =>
      self ! Handled(update.id)
      handleCommands(sender())

    case someCommand if handleCommands(sender()).isDefinedAt(someCommand) =>
      handleCommands(sender())

    case unhandled =>
      log.warning(s"unhandled message $unhandled")
  }

  def handleCommands(requester: ActorRef): PartialFunction[Any, Unit] = {
    case c: SendPhoto => sendPhoto(c).logoutResult
    case c: SendAudio => sendAudio(c).logoutResult
    case c: SendChatAction => sendChatAction(c).logoutResult
    case c: SendDocument => sendDocument(c).logoutResult
    case c: SendLocation => sendLocation(c).logoutResult
    case c: SendMessage => sendMessage(c).logoutResult
    case c: SendPhoto => sendPhoto(c).logoutResult
    case c: SendSticker => sendSticker(c).logoutResult
    case c: SendVideo => sendVideo(c).logoutResult
    case c: SendVoice => sendVoice(c).logoutResult
    case c: GetFile => getFile(c).logoutResult
    case c: GetUserProfilePhotos => getUserProfilePhotos(c).logoutResult
  }


  implicit def wrapIntoLoggable[T](f: Future[Response[T]]): Object {def logoutResult: Future[Response[T]]} = new {
    def logoutResult = {
      f.onComplete {
        case Success(result) =>
          log.debug(s"request end with $result")
        case Failure(t) =>
          log.error(t, "error during executing request")
      }
      f
    }

  }

  override def receive: Receive = initializing

  override implicit def actorSystem: ActorSystem = context.system
}
