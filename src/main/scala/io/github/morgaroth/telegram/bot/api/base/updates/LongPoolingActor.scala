package io.github.morgaroth.telegram.bot.api.base.updates

import java.io.File

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import io.github.morgaroth.telegram.bot.api.base.methods.{GetUpdatesReq, Method0, Method1, SetWebHookReq}
import io.github.morgaroth.telegram.bot.api.base.models.{Update, User}
import spray.http.MultipartFormData
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
 * Created by mateusz on 18.09.15.
 */

trait Methods extends SprayJsonSupport with DefaultJsonProtocol {
  implicit def actorSystem: ActorSystem

  def botToken: String

  lazy val getMe = new Method0[User]("getMe", botToken)
  lazy val getUpdates = new Method1[GetUpdatesReq, List[Update]]("getUpdates", botToken)
  lazy val setWebHook = new Method1[MultipartFormData, String]("setWebhook", botToken)
  lazy val unsetWebHook = new Method1[MultipartFormData, String]("setWebhook", botToken)
    .compose[Unit]((a:Unit) => SetWebHookReq("").toMultipartFormData)

}

object LongPoolingActor {
  def props(botToken: String) = Props(classOf[LongPoolingActor], botToken)
}

class LongPoolingActor(val botToken: String) extends Actor with ActorLogging with Methods {

  import context.dispatcher

  getMe().onComplete(x => println(s"get me result $x"))
  getUpdates(GetUpdatesReq()).onComplete(x => println(s"get me result $x"))
  setWebHook(SetWebHookReq("https://example.com/sdfdsfasd/callback", Some(new File("certificate"))).toMultipartFormData)

  override def receive: Receive = {
    case _ =>
  }

  override def actorSystem: ActorSystem = context.system
}
