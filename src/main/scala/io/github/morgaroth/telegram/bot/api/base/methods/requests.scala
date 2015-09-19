package io.github.morgaroth.telegram.bot.api.base.methods

import java.io.File

import spray.http._
import us.bleibinha.spray.json.macros.lazyy.json

/**
 * Created by mateusz on 19.09.15.
 */
@json case class GetUpdatesReq(
                                offset: Option[Int] = None,
                                limit: Option[Int] = None,
                                timeout: Option[Int] = None)

case class SetWebHookReq(url: String, certificate: Option[File] = None) {
  def toFormData: FormData = FormData(Map("url" -> url))

  def toMultipartFormData = MultipartFormData(
    Seq(BodyPart(HttpEntity(url), "url"))
      ++ certificate.map(BodyPart(_, "certificate"))
  )
}

object SetWebHookReq {
  def apply(url: String, certificate: File): MultipartFormData = apply(url, Some(certificate)).toMultipartFormData

  def apply(url: String): FormData = apply(url, None).toFormData

  def unset: FormData = apply("", None).toFormData
}