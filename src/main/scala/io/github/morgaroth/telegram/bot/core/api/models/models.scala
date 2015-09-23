package io.github.morgaroth.telegram.bot.core.api.models

import io.github.morgaroth.telegram.bot.core.api.models.formats._
import spray.http.{BodyPart, FormData, HttpEntity, MultipartFormData}
import spray.json._
import spray.json.DefaultJsonProtocol._
import us.bleibinha.spray.json.macros.jsonstrict
import us.bleibinha.spray.json.macros.lazyy.json
sealed trait Command


object formats {
  type MultiMaybeForm = Either[MultipartFormData, FormData]
  type Keyboard = Either[Either[ReplyKeyboardMarkup, ReplyKeyboardHide], ForceReply]
  type DI = DummyImplicit

  def convBP(t: (String, java.io.File))(implicit di: DI): BodyPart = BodyPart(t._2, t._1)

  def convBP(t: (String, Int))(implicit di: DI, di2: DI): BodyPart = BodyPart(HttpEntity(t._2.toString), t._1)

  def convBP(t: (String, String))(implicit di: DI, di2: DI, di3: DI): BodyPart = BodyPart(HttpEntity(t._2), t._1)

  def convBP(t: (String, Keyboard))(implicit di: DI, di2: DI, di3: DI, di4: DI): BodyPart = BodyPart(HttpEntity(t._2.toJson.compactPrint), t._1)

  def convFD(t: (String, Int))(implicit di: DI) = t._1 -> t._2.toString

  def convFD(t: (String, String))(implicit di: DI, di2: DI) = t

  def convFD(t: (String, Keyboard)) = t._1 -> t._2.toJson.compactPrint

}

/**
 * https://core.telegram.org/bots/api#audio
 */
@json case class Audio(
                        file_id: String,
                        duration: Int,
                        mime_type: String,
                        file_size: Int,
                        title: String,
                        performer: String
                        )

/**
 * https://core.telegram.org/bots/api#contact
 */
@json case class Contact(
                          phone_number: String,
                          first_name: String,
                          last_name: Option[String],
                          user_id: Option[Int]
                          )

/**
 * https://core.telegram.org/bots/api#document
 */
@json case class Document(
                           file_id: String,
                           thumb: PhotoSize,
                           file_name: Option[String],
                           mime_type: Option[String],
                           file_size: Option[Int]
                           )

/**
 * https://core.telegram.org/bots/api#file
 */
@json case class File(
                       file_id: String,
                       file_size: Option[Int],
                       file_path: Option[String]
                       )

/**
 * https://core.telegram.org/bots/api#groupchat
 */
@json case class GroupChat(
                            id: Int,
                            title: String
                            )

/**
 * https://core.telegram.org/bots/api#location
 */
@json case class Location(
                           longitude: Double,
                           latitude: Double
                           )

/**
 * https://core.telegram.org/bots/api#message
 */
@json case class Message(
                          message_id: Int,
                          from: User,
                          date: Long,
                          chat: Either[User, GroupChat],
                          forward_from: Option[User],
                          forward_date: Option[Long],
                          text: Option[String],
                          audio: Option[Audio],
                          document: Option[Document],
                          photo: Option[List[PhotoSize]],
                          sticker: Option[Sticker],
                          video: Option[Video],
                          contact: Option[Contact],
                          location: Option[Location],
                          new_chat_participant: Option[User],
                          left_chat_participant: Option[User],
                          new_chat_title: Option[String],
                          new_chat_photo: Option[List[PhotoSize]],
                          delete_chat_photo: Option[Boolean],
                          group_chat_created: Option[Boolean],
                          reply_to_message: Option[Message]
                          ) {
  def chatId = chat.fold(_.id, _.id)
}

/**
 * https://core.telegram.org/bots/api#photosize
 */
@json case class PhotoSize(
                            file_id: String,
                            width: Int,
                            height: Int,
                            file_size: Option[Int]
                            )

/**
 * https://core.telegram.org/bots/api#replykeyboardmarkup
 */
@json case class ReplyKeyboardMarkup(
                                      keyboard: List[List[String]],
                                      resize_keyboard: Option[Boolean],
                                      one_time_keyboard: Option[Boolean],
                                      selective: Option[Boolean]
                                      )

/**
 * https://core.telegram.org/bots/api#replykeyboardhide
 */
@json case class ReplyKeyboardHide(
                                    hide_keyboard: Boolean = true,
                                    selective: Option[Boolean]
                                    )

/**
 * https://core.telegram.org/bots/api#forcereply
 */
@json case class ForceReply(
                             force_reply: Boolean = true,
                             selective: Option[Boolean]
                             )

/**
 * https://core.telegram.org/bots/api#sticker
 */
@json case class Sticker(
                          file_id: String,
                          width: Int,
                          height: Int,
                          file_size: Option[Int]
                          )

/**
 * https://core.telegram.org/bots/api#user
 */
@json case class User(
                       id: Int,
                       first_name: String,
                       last_name: Option[String],
                       username: Option[String]
                       )

/**
 * https://core.telegram.org/bots/api#userprofilephotos
 */
@json case class UserProfilePhotos(
                                    total_count: Int,
                                    photos: List[PhotoSize]
                                    )

/**
 * https://core.telegram.org/bots/api#update
 */
@json case class Update(
                         update_id: Int,
                         message: Message
                         )

/**
 * https://core.telegram.org/bots/api#video
 */
@json case class Video(
                        file_id: String,
                        width: Int,
                        height: Int,
                        duration: Int,
                        thumb: PhotoSize,
                        mime_type: Option[String],
                        file_size: Option[Int]
                        )

/**
 * https://core.telegram.org/bots/api#voice
 */
@json case class Voice(
                        file_id: String,
                        duration: Int,
                        mime_type: Option[String],
                        file_size: Option[Int]
                        )

/**
 * https://core.telegram.org/bots/api#sendmessage
 */
@json case class SendMessage(
                              chat_id: Int,
                              text: String,
                              parse_mode: Option[String],
                              disable_web_page_preview: Option[Boolean],
                              reply_to_message_id: Option[Int],
                              reply_markup: Option[Keyboard]
                              ) extends Command

/**
 * https://core.telegram.org/bots/api#forwardmessage
 */
@json case class ForwardMessage(
                                 chat_id: Int,
                                 from_chat_id: Int,
                                 message_id: Int
                                 ) extends Command

/**
 * https://core.telegram.org/bots/api#sendphoto
 */
case class SendPhoto(
                      chat_id: Int,
                      photo: Either[java.io.File, String],
                      caption: Option[String],
                      reply_to_message_id: Option[Int],
                      reply_markup: Option[Keyboard]
                      ) extends Command {
  def toForm: MultiMaybeForm =
    photo.left.map(data =>
      MultipartFormData(
        Seq(convBP("chat_id" -> chat_id), convBP("photo" -> data)) ++
          caption.map(x => convBP("caption" -> x)) ++
          reply_to_message_id.map(x => convBP("reply_to_message_id" -> x)) ++
          reply_markup.map(x => convBP("reply_markup" -> x))
      )).right.map(data_id =>
      FormData(
        Seq(convFD("chat_id" -> chat_id), convFD("photo" -> data_id)) ++
          caption.map(x => convFD("caption" -> x)) ++
          reply_to_message_id.map(x => convFD("reply_to_message_id" -> x)) ++
          reply_markup.map(x => convFD("reply_markup" -> x))
      ))

}

/**
 * https://core.telegram.org/bots/api#sendaudio
 */
case class SendAudio(
                      chat_id: Int,
                      audio: Either[java.io.File, String],
                      duration: Option[Int],
                      performer: Option[String],
                      title: Option[String],
                      reply_to_message_id: Option[Int],
                      reply_markup: Option[Keyboard]
                      ) extends Command {
  def toForm: MultiMaybeForm =
    audio.left.map(data =>
      MultipartFormData(
        Seq(convBP("chat_id" -> chat_id), convBP("audio" -> data)) ++
          duration.map(x => convBP("duration" -> x)) ++
          performer.map(x => convBP("performer" -> x)) ++
          title.map(x => convBP("title" -> x)) ++
          reply_to_message_id.map(x => convBP("reply_to_message_id" -> x)) ++
          reply_markup.map(x => convBP("reply_markup" -> x))
      )).right.map(data_id =>
      FormData(
        Seq(convFD("chat_id" -> chat_id), convFD("audio" -> data_id)) ++
          duration.map(x => convFD("duration" -> x)) ++
          performer.map(x => convFD("performer" -> x)) ++
          title.map(x => convFD("title" -> x)) ++
          reply_to_message_id.map(x => convFD("reply_to_message_id" -> x)) ++
          reply_markup.map(x => convFD("reply_markup" -> x))
      ))

}

/**
 * https://core.telegram.org/bots/api#senddocument
 */
case class SendDocument(
                         chat_id: Int,
                         document: Either[java.io.File, String],
                         reply_to_message_id: Option[Int],
                         reply_markup: Option[Keyboard]
                         ) extends Command {
  def toForm: MultiMaybeForm =
    document.left.map(data =>
      MultipartFormData(
        Seq(convBP("chat_id" -> chat_id), convBP("document" -> data)) ++
          reply_markup.map(x => convBP("reply_markup" -> x)) ++
          reply_to_message_id.map(x => convBP("reply_to_message_id" -> x))
      )).right.map(data_id =>
      FormData(
        Seq(convFD("chat_id" -> chat_id), convFD("document" -> data_id)) ++
          reply_markup.map(x => convFD("reply_markup" -> x)) ++
          reply_to_message_id.map(x => convFD("reply_to_message_id" -> x))
      ))
}

/**
 * https://core.telegram.org/bots/api#sendsticker
 */
case class SendSticker(
                        chat_id: Int,
                        sticker: Either[java.io.File, String],
                        reply_to_message_id: Option[Int],
                        reply_markup: Option[Keyboard]
                        ) extends Command {
  def toForm: MultiMaybeForm =
    sticker.left.map(data =>
      MultipartFormData(
        Seq(convBP("chat_id" -> chat_id), convBP("sticker" -> data)) ++
          reply_markup.map(x => convBP("reply_markup" -> x)) ++
          reply_to_message_id.map(x => convBP("reply_to_message_id" -> x))
      )).right.map(data_id =>
      FormData(
        Seq(convFD("chat_id" -> chat_id), convFD("sticker" -> data_id)) ++
          reply_markup.map(x => convFD("reply_markup" -> x)) ++
          reply_to_message_id.map(x => convFD("reply_to_message_id" -> x))
      ))

}

/**
 * https://core.telegram.org/bots/api#sendvideo
 */
case class SendVideo(
                      chat_id: Int,
                      video: Either[java.io.File, String],
                      duration: Option[Int],
                      caption: Option[String],
                      reply_to_message_id: Option[Int],
                      reply_markup: Option[Keyboard]
                      ) extends Command {
  def toForm: MultiMaybeForm =
    video.left.map(data =>
      MultipartFormData(
        Seq(convBP("chat_id" -> chat_id), convBP("video" -> data)) ++
          duration.map(x => convBP("duration" -> x)) ++
          caption.map(x => convBP("caption" -> x)) ++
          reply_markup.map(x => convBP("reply_markup" -> x)) ++
          reply_to_message_id.map(x => convBP("reply_to_message_id" -> x))
      )).right.map(data_id =>
      FormData(
        Seq(convFD("chat_id" -> chat_id), convFD("voice" -> data_id)) ++
          duration.map(x => convFD("duration" -> x)) ++
          caption.map(x => convFD("caption" -> x)) ++
          reply_markup.map(x => convFD("reply_markup" -> x)) ++
          reply_to_message_id.map(x => convFD("reply_to_message_id" -> x))
      ))

}

/**
 * https://core.telegram.org/bots/api#sendvoice
 */
case class SendVoice(
                      chat_id: Int,
                      voice: Either[java.io.File, String],
                      duration: Option[Int],
                      reply_to_message_id: Option[Int],
                      reply_markup: Option[Keyboard]
                      ) extends Command {
  def toForm: MultiMaybeForm = {
    voice.left.map(voice =>
      MultipartFormData(
        Seq(convBP("chat_id" -> chat_id),
          convBP("voice" -> voice)
        ) ++
          duration.map(x => convBP("duration" -> x)) ++
          reply_markup.map(x => convBP("reply_markup" -> x)) ++
          reply_to_message_id.map(x => convBP("reply_to_message_id" -> x))
      )).right.map(voice_id =>
      FormData(
        Seq(convFD("chat_id" -> chat_id), convFD("voice" -> voice_id)) ++
          duration.map(x => convFD("duration" -> x)) ++
          reply_markup.map(x => convFD("reply_markup" -> x)) ++
          reply_to_message_id.map(x => convFD("reply_to_message_id" -> x))
      ))
  }
}

/**
 * https://core.telegram.org/bots/api#sendlocation
 */
@json case class SendLocation(
                               chat_id: Int,
                               latitude: Double,
                               longitude: Double,
                               reply_to_message_id: Option[Int],
                               reply_markup: Option[Keyboard]
                               ) extends Command

/**
 * https://core.telegram.org/bots/api#sendchataction
 */
@json case class SendChatAction(
                                 chat_id: Int,
                                 action: Action
                                 ) extends Command

@jsonstrict case class Action(name: String)

object Action {
  val `typing...` = Action("typing")
  val `uploading photo...` = Action("upload_photo")
  val `uploading document...` = Action("upload_document")
  val `location...` = Action("find_location")
  val `recording video...` = Action("record_video")
  val `uploading video...` = Action("upload_video")
  val `recording audio...` = Action("record_audio")
  val `uploading audio...` = Action("upload_audio")
}

/**
 * https://core.telegram.org/bots/api#getuserprofilephotos
 */
@json case class GetUserProfilePhotos(
                                       user_id: Int,
                                       offset: Option[Int],
                                       limit: Option[Int]
                                       ) extends Command

/**
 * https://core.telegram.org/bots/api#getfile
 */
@json case class GetFile(
                          file_id: String
                          ) extends Command