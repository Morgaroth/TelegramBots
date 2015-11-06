package io.github.morgaroth.telegram.bot.core.api

import io.github.morgaroth.telegram.bot.core.api.models.formats.Keyboard

import scala.language.implicitConversions

/**
  * Created by mateusz on 24.09.15.
  */
package object models {


  case class Chat(private val chat: Either[User, GroupChat]) {
    def chatId: Int = chat.fold(_.id, _.id)

    def isGroupChat: Boolean = chat.isRight

    def isPrvChat: Boolean = chat.isLeft

    def uber = chat.fold(u => UberUser(u.id, u.first_name, "user", u.last_name, u.username), g => UberUser(g.id, g.title, "group", None, None))

    def msg(text: String,
            parse_mode: Option[String] = None,
            disable_web_page_preview: Option[Boolean] = None,
            reply_to_message_id: Option[Int] = None,
            reply_markup: Option[Keyboard] = None) =
      SendMessage(chatId, text, parse_mode, disable_web_page_preview,
        reply_to_message_id, reply_markup)
  }

  //  type Chat = Either[User, GroupChat]

  implicit def convertToChat(chat: Either[User, GroupChat]): Chat = new Chat(chat)
}
