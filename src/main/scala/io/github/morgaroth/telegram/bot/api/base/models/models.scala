package io.github.morgaroth.telegram.bot.api.base.models

import us.bleibinha.spray.json.macros.lazyy.json

@json case class Audio(
                        file_id: String,
                        duration: Int,
                        mime_type: String,
                        file_size: Int,
                        title: String,
                        performer: String
                        )


@json case class Chat(
                       id: String,
                       first_name: String,
                       last_name: Option[String],
                       title: String,
                       username: Option[String]
                       )

@json case class Contact(
                          phone_number: String,
                          first_name: String,
                          last_name: Option[String],
                          user_id: Option[Int]
                          )

@json case class Document(
                           file_id: String,
                           thumb: PhotoSize,
                           file_name: Option[String],
                           mime_type: Option[String],
                           file_size: Option[Int]
                           )

@json case class Location(
                           longitude: Double,
                           latitude: Double
                           )

@json case class Message(
                          message_id: Int,
                          from: User,
                          date: Long,
                          chat: Chat,
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
                          reply_to_message: Message
                          )

@json case class PhotoSize(
                            file_id: String,
                            width: Int,
                            height: Int,
                            file_size: Option[Int]
                            )

@json case class Sticker(
                          file_id: String,
                          width: Int,
                          height: Int,
                          file_size: Option[Int]
                          )

@json case class User(
                       id: Int,
                       first_name: String,
                       last_name: Option[String],
                       username: Option[String]
                       )

@json case class UserProfilePhotos(
                                    total_count: Int,
                                    photos: List[PhotoSize]
                                    )

@json case class Update(
                         update_id: Int,
                         message: Option[Message]
                         )

@json case class Video(
                        file_id: String,
                        width: Int,
                        height: Int,
                        duration: Int,
                        thumb: PhotoSize,
                        mime_type: Option[String],
                        file_size: Option[Int]
                        )

@json case class Voice(
                        file_id: String,
                        duration: Int,
                        mime_type: Option[String],
                        file_size: Option[Int]
                        )