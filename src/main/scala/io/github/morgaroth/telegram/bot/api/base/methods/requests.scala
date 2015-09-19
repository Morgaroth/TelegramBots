package io.github.morgaroth.telegram.bot.api.base.methods

import java.io.File

import spray.http._
import spray.httpx.marshalling.{MarshallingContext, Marshaller}
import us.bleibinha.spray.json.macros.lazyy.json

/**
 * Created by mateusz on 19.09.15.
 */
@json case class GetUpdatesReq(
                                offset: Option[Int] = None,
                                limit: Option[Int] = None,
                                timeout: Option[Int] = None)

case class SetWebHookReq(url: String, certificate: Option[File] = None) {

  val maps: Seq[BodyPart] = Seq(
    BodyPart(HttpEntity.apply(url), "url")
  ) ++ certificate.map(BodyPart(_, "certificate"))

  def toMultipartFormData = MultipartFormData(maps)
}

object SetWebHookReq {
  def apply(url: String, certificate: File): SetWebHookReq = apply(url, Some(certificate))
}