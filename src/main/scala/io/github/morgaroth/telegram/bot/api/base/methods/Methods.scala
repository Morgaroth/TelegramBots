package io.github.morgaroth.telegram.bot.api.base.methods

import akka.actor.ActorSystem
import io.github.morgaroth.telegram.bot.api.base.models._
import io.github.morgaroth.telegram.bot.api.base.models.formats.MultiMaybeForm
import spray.http.{FormData, MultipartFormData}
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
 * Created by mateusz on 19.09.15.
 */
trait Methods extends SprayJsonSupport with DefaultJsonProtocol {
  implicit def actorSystem: ActorSystem

  def botToken: String

  def m1[R, T](name: String) = new Method1[R, T](name, botToken = botToken)

  def m0[T](name: String) = new Method0[T](name, botToken = botToken)

  lazy val getMe = m0[User]("getMe")

  lazy val getUpdates = m1[GetUpdatesReq, List[Update]]("getUpdates")
  lazy val setWebHook = m1[MultipartFormData, Boolean]("setWebhook")
  lazy val unsetWebHook = m1[FormData, Boolean]("setWebhook").compose[Unit](x => SetWebHookReq.unset)

  def m1m[R](name: String) = m1[R, Message](name)

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