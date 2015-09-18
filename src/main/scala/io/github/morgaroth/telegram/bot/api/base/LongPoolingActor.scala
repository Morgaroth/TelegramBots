package io.github.morgaroth.telegram.bot.api.base

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import io.github.morgaroth.telegram.bot.api.base.models.User
import spray.httpx.SprayJsonSupport
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling._
import spray.json.{DefaultJsonProtocol, JsonFormat}
import us.bleibinha.spray.json.macros.lazyy.json

import scala.concurrent.Future

/**
 * Created by mateusz on 18.09.15.
 */

case class Response[T](ok: Boolean, result: Either[String, T])

object Response {

  import DefaultJsonProtocol._

  implicit def namedListFormat[A: JsonFormat] = jsonFormat2(Response.apply[A])
}

@json case class GetUpdatesReq(offset: Option[Int], limit: Option[Int], timeout: Option[Int])

import spray.client.pipelining._

trait MethodValues extends SprayJsonSupport with DefaultJsonProtocol {
  implicit def as: ActorSystem

  implicit def ex = as.dispatcher

  def botToken: String

  val service = "https://api.telegram.org"

  def uri(method: String): String = {
    s"$service/bot$botToken/$method"
  }
}

abstract class Method1[D: Marshaller, R: JsonFormat] extends ((D) => Future[Response[R]]) {
  this: MethodValues =>
  val endpoint: String

  override def apply(data: D): Future[Response[R]] = {
    val pipe = sendReceive ~> unmarshal[Response[R]]
    pipe(Post(uri(endpoint), data))
  }

}

abstract class Method0[R: JsonFormat](endpoint: String) extends (() => Future[Response[R]]) {
  this: MethodValues =>

  override def apply(): Future[Response[R]] = {
    val pipe = sendReceive ~> unmarshal[Response[R]]
    pipe(Post(uri(endpoint)))
  }
}

trait Methods extends SprayJsonSupport with DefaultJsonProtocol {
  def actorSystem: ActorSystem

  def botToken: String

  trait ThisBotValues extends MethodValues {
    override implicit def as: ActorSystem = actorSystem

    override def botToken: String = Methods.this.botToken
  }


  val getMe = new Method0[User]("getMe") with ThisBotValues

}


object LongPoolingActor {
  def props(botToken: String) = Props(classOf[LongPoolingActor], botToken)
}

class LongPoolingActor(val botToken: String) extends Actor with ActorLogging with Methods {

  import context.dispatcher

  getMe().onComplete(x => println(s"get me result $x"))

  override def receive: Receive = {
    case _ =>
  }

  override def actorSystem: ActorSystem = context.system
}
