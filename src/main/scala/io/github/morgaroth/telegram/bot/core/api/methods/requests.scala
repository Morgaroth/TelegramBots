package io.github.morgaroth.telegram.bot.core.api.methods

import java.io.File

import spray.http._
import us.bleibinha.spray.json.macros.lazyy.json

import scala.concurrent.duration.FiniteDuration

/**
 * Created by mateusz on 19.09.15.
 */
case class GetUpdatesReq(
                          offset: Option[Int] = None,
                          limit: Option[Int] = None,
                          timeout: Option[Int] = None
                          )

object GetUpdatesReq {

  import spray.json.DefaultJsonProtocol._

  implicit val serializer = jsonFormat3(GetUpdatesReq.apply(_: Option[Int], _: Option[Int], _: Option[Int]))

  def apply(offset: Option[Int], limit: Int): GetUpdatesReq =
    apply(offset, Some(limit))

  def apply(offset: Option[Int], timeout: FiniteDuration): GetUpdatesReq =
    apply(offset, None, Some(timeout.toSeconds.toInt))

  def apply(offset: Option[Int], limit: Int, timeout: FiniteDuration): GetUpdatesReq =
    apply(offset, Some(limit), Some(timeout.toSeconds.toInt))

}

class SetWebHookReq(url: String, certificate: Option[File] = None) {
  def toFormData: FormData = FormData(Map("url" -> url))

  def toMultipartFormData = MultipartFormData(
    Seq(BodyPart(HttpEntity(url), "url"))
      ++ certificate.map(BodyPart(_, "certificate"))
  )
}

object SetWebHookReq {
  def apply(url: String, cert: Option[File]): SetWebHookReq = new SetWebHookReq(url, cert)

  def apply(url: String, certificate: File): MultipartFormData = apply(url, Some(certificate)).toMultipartFormData

  def apply(url: String): FormData = apply(url, None).toFormData

  def unset: FormData = apply("", None).toFormData
}