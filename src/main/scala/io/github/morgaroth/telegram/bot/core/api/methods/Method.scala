package io.github.morgaroth.telegram.bot.core.api.methods

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.event.Logging
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.client.pipelining._
import spray.httpx.SprayJsonSupport
import spray.httpx.marshalling.Marshaller
import spray.json.{DefaultJsonProtocol, JsonFormat}

import scala.concurrent.Future
import scala.concurrent.duration._
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

  def loggedSendReceive = {
    val reqIq = UUID.randomUUID()
    logRequest(request => log.debug(s"req $reqIq ${request.toString.replaceAll("[\n\t]+", " ")}")) ~> sendReceive ~> logResponse(x => log.debug(s"req $reqIq ${x.toString}"))
  }
}

class Method1[D: Marshaller, R: JsonFormat](endpoint: String, val botToken: String)(implicit val as: ActorSystem) extends ((D) => Future[Response[R]]) with MethodsCommons {
  override def apply(data: D): Future[Response[R]] = {
    val pipe = loggedSendReceive ~> unmarshal[Response[R]]
    pipe(Post(uri(endpoint), data))
  }
}

class Method0[R: JsonFormat](endpoint: String, val botToken: String)(implicit val as: ActorSystem) extends (() => Future[Response[R]]) with MethodsCommons {
  this: MethodsCommons =>

  override def apply(): Future[Response[R]] = {
    val pipe = loggedSendReceive ~> unmarshal[Response[R]]
    pipe(Post(uri(endpoint)))
  }
}

class FileFetch(val botToken: String)(implicit val as: ActorSystem) extends ((String) => Future[Array[Byte]]) with MethodsCommons {
  this: MethodsCommons =>
  override def apply(filePath: String): Future[Array[Byte]] = {
    val pipe = loggedSendReceive
    pipe(Get(s"$service/file/bot$botToken/$filePath")).map(_.entity.data.toByteArray)
  }
}