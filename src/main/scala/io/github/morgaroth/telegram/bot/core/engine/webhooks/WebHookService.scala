package io.github.morgaroth.telegram.bot.core.engine.webhooks

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import io.github.morgaroth.telegram.bot.core.api.methods.Response
import io.github.morgaroth.telegram.bot.core.api.models.Update
import io.github.morgaroth.telegram.bot.core.engine.NewUpdate
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport
import spray.routing.Directives

import scala.language.{implicitConversions, reflectiveCalls}

/**
 * Created by mateusz on 19.09.15.
 */
class WebHookService(webHookManager: ActorRef)(implicit actorSystem: ActorSystem) extends Directives with SprayJsonSupport {
  val log = Logging(actorSystem, getClass)

  def handleUpdate(reqID: UUID, botId: String)(request: Response[Update]) = {
    webHookManager ! NewUpdate(reqID, botId, request.result.right.get)
    StatusCodes.OK
  }

  implicit def normalizable(str: String): Object {def normalize: String} = new {
    def normalize = str.replaceAll( """[\n\t\r]+""", " ")
  }

  //@formatter:off
  val route = {
    pathEndOrSingleSlash(get(complete("Hello from WebHook service"))) ~
    pathPrefix("callbacks" / Segment) { botId =>
      (pathEndOrSingleSlash & post) {
        provide(UUID.randomUUID()) { reqId =>
          extract(_.request.entity) { entity =>
            extract(_.request.headers) { headers =>
              // sould be managed by config
              log.debug(s"request $reqId from bot $botId")
              log.debug(s"request $reqId entity ${entity.asString.normalize}")
              log.debug(s"request $reqId headers ${headers.mkString}")
              handleWith(handleUpdate(reqId, botId))
            }
          }
        }
      }
    }
  }
  //@formatter:on
}
