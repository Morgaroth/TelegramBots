package io.github.morgaroth.telegram.bot.core.engine.webhooks

import akka.pattern.ask
import akka.actor._
import akka.io.IO
import akka.util.Timeout
import io.github.morgaroth.telegram.bot.core.api.methods.{Methods, Response, SetWebHookReq}
import io.github.morgaroth.telegram.bot.core.engine.webhooks.WebHookManager._
import io.github.morgaroth.telegram.bot.core.engine.{NewUpdate, WebHookSettings}
import spray.can.Http
import spray.routing.HttpServiceActor

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * Created by mateusz on 20.09.15.
 */

object WebHookManager {

  //@formatter:off
  case class Register(botId: String, botToken: String, botActor: ActorRef)
  case object Registered
  case class RegisteringFailed(reason: Either[Response[Boolean], Throwable])
  case class UnRegister(botId: String, botToken: String)
  case object Unregistered
  case class UnregisteringFailed(reason: Either[Response[Boolean], Throwable])
  private[WebHookManager] case class BotDefinition(bot: ActorRef, botToken: String)
  //@formatter:on


  def props(settings: WebHookSettings) = Props(classOf[WebHookManager], settings)
}

class WebHookManager(settings: WebHookSettings) extends Actor with ActorLogging {

  import context.dispatcher

  implicit val as = context.system
  var registered = Map.empty[String, BotDefinition]
  lazy val deadLetters = context.actorOf(Props[DeadLetters], "dead-updates")

  lazy val service = bind(new WebHookService(self))

  def bind(service: WebHookService) = {
    val serviceActorProps = Props(new HttpServiceActor {
      override def receive: Actor.Receive = runRoute(service.route)
    })
    val rootService = as.actorOf(serviceActorProps)
    implicit val tm: Timeout = 10 seconds
    val result = Await.result(IO(Http) ? Http.Bind(rootService, "0.0.0.0", settings.bindPort), 10 seconds)
    log.info(s"Binding WebHookService end with $result")
    service
  }

  def unbind() = {
    IO(Http) ! Http.Unbind
  }

  def getService = service

  override def receive: Receive = {
    case update: NewUpdate =>
      registered.get(update.botId).map(_.bot).getOrElse(deadLetters) ! update

    case Register(botId, botToken, botActor) =>
      getService
      log.info(s"Registering botId $botId with updates receiver $botActor.")
      registered += botId -> BotDefinition(botActor, botToken)
      val s = sender()
      setWebHook(botId, botToken).onComplete {
        case Success(Response(true, _, _)) => s ! Registered
        case Success(r@Response(false, _, _)) => s ! RegisteringFailed(Left(r))
        case Failure(t) => s ! RegisteringFailed(Right(t))
      }

    case UnRegister(botId, botToken) =>
      log.info(s"Removing botId $botId(${registered.get(botId)}) from registry.")
      registered -= botId
      val s = sender()
      unsetWebHook(botToken).onComplete {
        case Success(Response(true, _, _)) => s ! Unregistered
        case Success(r@Response(false, _, _)) => s ! UnregisteringFailed(Left(r))
        case Failure(t) => s ! UnregisteringFailed(Right(t))
      }

    case unhandled =>
      log.warning(s"Unhandled message $unhandled.")
  }

  val domain = settings.domain.stripPrefix("https://").stripPrefix("http://").stripSuffix("/")

  def setWebHook(botId: String, botToken: String): Future[Response[Boolean]] = {
    val whUrl = urlForBot(botId)
    Methods(botToken).setWebHook(SetWebHookReq(whUrl, settings.certificate).toMultipartFormData)
  }

  def unsetWebHook(botToken: String): Future[Response[Boolean]] = {
    Methods(botToken).unsetWebHook(SetWebHookReq.unset)
  }

  def urlForBot(botId: String): String = s"https://$domain/callbacks/$botId"

  def unregisterAll() = {
    registered.foreach {
      case (botId, BotDefinition(bot, botToken)) =>
        log.info(s"Unregistering botId $botId(${registered.get(botId)}) on manager shutdown.")
        unsetWebHook(botToken).onComplete {
          case Success(Response(true, _, _)) => bot ! Unregistered
          case Success(r@Response(false, _, _)) => bot ! UnregisteringFailed(Left(r))
          case Failure(t) => bot ! UnregisteringFailed(Right(t))
        }
    }
  }

  override def postStop(): Unit = {
    unbind()
    unregisterAll()
    super.postStop()
  }
}


class DeadLetters extends Actor with ActorLogging {
  override def receive: Actor.Receive = {
    case NewUpdate(id, botId, update) =>
      log.warning(s"Received update for bot $id, but nobody is registered to handle it. update is $update")
  }
}