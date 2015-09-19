package io.github.morgaroth.telegram.bot.api.base.methods

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.event.Logging
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import jdk.nashorn.internal.runtime.regexp.joni.Config
import spray.client.pipelining._
import spray.httpx.SprayJsonSupport
import spray.httpx.marshalling.Marshaller
import spray.json.{JsonFormat, DefaultJsonProtocol}
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.postfixOps

/**
 * Created by mateusz on 19.09.15.
 */

case class Response[T](ok: Boolean, result: Either[String, T], description: Option[String])

object Response {

  import DefaultJsonProtocol._

  implicit def namedListFormat[A: JsonFormat] = jsonFormat3(Response.apply[A])
}


trait MethodsCommons extends SprayJsonSupport with DefaultJsonProtocol {
  implicit def as: ActorSystem

  implicit val timeout: Timeout = ConfigFactory.load().getDuration("spray.can.client.request-timeout", TimeUnit.SECONDS) seconds
  lazy val log = Logging(as, getClass)

  implicit def ex = as.dispatcher

  def botToken: String

  val service = "https://api.telegram.org"

  def uri(method: String): String = {
    s"$service/bot$botToken/$method"
  }
}

class Method1[D: Marshaller, R: JsonFormat](endpoint: String, val botToken: String)(implicit val as: ActorSystem) extends ((D) => Future[Response[R]]) with MethodsCommons {
  override def apply(data: D): Future[Response[R]] = {
    //    val pipe = sendReceive ~> logResponse(x => log.info(x.toString)) ~> unmarshal[Response[R]]
    //    val pipe = logRequest(request => log.info(s"request to $endpoint is $request, entity ${request.entity.asString}")) ~> sendReceive ~> logResponse(x => log.info(x.toString)) ~> unmarshal[Response[R]]
    val pipe = sendReceive ~> unmarshal[Response[R]]
    pipe(Post(uri(endpoint), data))
  }
}

class Method0[R: JsonFormat](endpoint: String, val botToken: String)(implicit val as: ActorSystem) extends (() => Future[Response[R]]) with MethodsCommons {
  this: MethodsCommons =>

  override def apply(): Future[Response[R]] = {
    val pipe = sendReceive ~> unmarshal[Response[R]]
    pipe(Post(uri(endpoint)))
  }
}