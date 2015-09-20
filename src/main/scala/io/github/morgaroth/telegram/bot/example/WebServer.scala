package io.github.morgaroth.telegram.bot.example

import java.io.File

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.event.Logging
import akka.io.IO
import com.typesafe.config.ConfigFactory
import io.github.morgaroth.telegram.bot.core.api.methods.{Methods, SetWebHookReq}
import io.github.morgaroth.telegram.bot.core.engine.webhooks.WebHookService
import spray.can.Http
import spray.http.StatusCodes._
import spray.http.{HttpEntity, StatusCode}
import spray.routing._
import spray.util.LoggingContext

import scala.util.control.NonFatal

/**
 * Created by mateusz on 19.09.15.
 */
case class ErrorResponseException(responseStatus: StatusCode, response: Option[HttpEntity]) extends Exception

class RoutedHttpService(route: Route) extends Actor with HttpService with ActorLogging {

  implicit def actorRefFactory = context

  implicit val handler = ExceptionHandler {
    case NonFatal(ErrorResponseException(statusCode, entity)) => ctx =>
      ctx.complete(statusCode, entity)

    case NonFatal(e) => ctx => {
      log.error(e, InternalServerError.defaultMessage)
      ctx.complete(InternalServerError)
    }
  }

  def receive: Receive =
    runRoute(route)(handler, RejectionHandler.Default, context, RoutingSettings.default, LoggingContext.fromActorRefFactory)
}

object WebServer extends App with Directives with Methods {

  override implicit lazy val actorSystem: ActorSystem = ActorSystem("bots-server")

  override def botToken: String = args(0)

  import actorSystem.dispatcher


  val log = Logging(actorSystem, getClass)

  val callbacks = new WebHookService(actorSystem)

  val routes = {
    pathPrefix("bots") {
      callbacks.route
    }
  }

  val config = ConfigFactory.load().getConfig("telegram-api.webhook")
  val domain = config.getString("domain").stripPrefix("https://").stripPrefix("http://").stripSuffix("/")
  val certificatePath = config.getString("certificate")
  val botSecret = config.getString("bot-secret")

  log.info(s"used certificate file $certificatePath")
  private val certificateFile = new File(certificatePath)
  log.info(s"used certificate can read? ${certificateFile.canRead}.")
  val req = SetWebHookReq(s"https://$domain/bots/$botSecret/callbacks", certificateFile)

  setWebHook(req).onComplete(x => log.info(s"setting webhook $x"))

  val rootService = actorSystem.actorOf(Props(new RoutedHttpService(routes)))

  val port = {
    val conf = ConfigFactory.load().getConfig("telegram-api.http")
    conf.getInt("port")
  }

  IO(Http)(actorSystem) ! Http.Bind(rootService, "0.0.0.0", port = port)
}