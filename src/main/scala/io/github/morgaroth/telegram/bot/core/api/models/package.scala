package io.github.morgaroth.telegram.bot.core.api

import scala.language.implicitConversions

/**
 * Created by mateusz on 24.09.15.
 */
package object models {
  type Chat = Either[User, GroupChat]

  implicit def idableChat(chat: Chat): Object {def chatId: Int} = new {
    def chatId = chat.fold(_.id, _.id)
  }
}
