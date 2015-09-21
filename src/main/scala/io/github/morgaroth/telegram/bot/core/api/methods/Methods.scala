package io.github.morgaroth.telegram.bot.core.api.methods

import akka.actor.ActorSystem
import io.github.morgaroth.telegram.bot.core.api.models._
import io.github.morgaroth.telegram.bot.core.api.models.formats.{DI, MultiMaybeForm}
import spray.http.{FormData, MultipartFormData}
import spray.httpx.SprayJsonSupport
import spray.httpx.marshalling.Marshaller
import spray.json.{DefaultJsonProtocol, JsonFormat}


/**
 * Created by mateusz on 19.09.15.
 */
trait Methods extends SprayJsonSupport with DefaultJsonProtocol {
  implicit def actorSystem: ActorSystem

  def botToken: String

  def m1[R: Marshaller, T: JsonFormat](name: String) = new Method1[R, T](name, botToken = botToken)

  def m0[T: JsonFormat](name: String) = new Method0[T](name, botToken = botToken)

  lazy val getMe = m0[User]("getMe")

  lazy val getUpdates = m1[GetUpdatesReq, List[Update]]("getUpdates")
  lazy val setWebHook = m1[MultipartFormData, Boolean]("setWebhook")
  lazy val unsetWebHook = m1[FormData, Boolean]("setWebhook")

  def m1m[R: Marshaller](name: String) = m1[R, Message](name)

  lazy val sendPhoto = m1m[MultiMaybeForm]("sendPhoto").compose[SendPhoto](_.toForm)
  lazy val sendAudio = m1m[MultiMaybeForm]("sendAudio").compose[SendAudio](_.toForm)
  lazy val sendDocument = m1m[MultiMaybeForm]("sendDocument").compose[SendDocument](_.toForm)
  lazy val sendSticker = m1m[MultiMaybeForm]("sendSticker").compose[SendSticker](_.toForm)
  lazy val sendVideo = m1m[MultiMaybeForm]("sendVideo").compose[SendVideo](_.toForm)
  lazy val sendVoice = m1m[MultiMaybeForm]("sendVoice").compose[SendVoice](_.toForm)

  lazy val sendMessage = m1m[SendMessage]("sendMessage")
  lazy val forwardMessage = m1m[ForwardMessage]("forwardMessage")
  lazy val sendChatAction = m1[SendChatAction, Boolean]("sendChatAction")
  lazy val getUserProfilePhotos = m1[GetUserProfilePhotos, UserProfilePhotos]("getUserProfilePhotos")
  lazy val getFile = m1[GetFile, File]("getFile")
}

object Methods {
  def apply(botsToken: String, as: ActorSystem): Methods = new Methods {
    override implicit def actorSystem: ActorSystem = as

    override def botToken: String = botsToken
  }

  def apply(botToken: String)(implicit as: ActorSystem, di: DI): Methods = apply(botToken, as)
}