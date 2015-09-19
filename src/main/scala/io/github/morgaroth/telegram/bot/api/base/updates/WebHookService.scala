package io.github.morgaroth.telegram.bot.api.base.updates

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.Logging
import spray.routing.Directives

/**
 * Created by mateusz on 19.09.15.
 */
class WebHookService(actorSystem: ActorSystem) extends Directives {
  val log = Logging(actorSystem, getClass)

  //@formatter:off
  val route = {
    pathEndOrSingleSlash {
      get {
        complete("Hello from WebHook service")
      }
    } ~
    pathPrefix(Segment / "callbacks") { botSecret =>
      post {
        provide(UUID.randomUUID()) { reqId =>
          extract(_.request.entity) { entity =>
            extract(_.request.headers) { headers =>
              println(s"request $reqId from bot $botSecret")
              println(s"request $reqId entity ${entity.asString}")
              println(s"request $reqId headers ${headers.mkString}")
              complete("")
            }
          }
        }
      }
    }
  }
  //@formatter:on
}
