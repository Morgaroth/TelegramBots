package io.github.morgaroth.telegram.bot.core.api

import scala.language.implicitConversions

/**
  * Created by mateusz on 24.09.15.
  */
package object models {


  type Chat = Either[User, GroupChat]

  implicit def idableChat(chat: Chat): Object {def isGroupChat: Boolean; def isPrvChat: Boolean; def chatId: Int; def uber: UberUser} = new {
    def chatId: Int = chat.fold(_.id, _.id)

    def isGroupChat: Boolean = chat.isRight

    def isPrvChat: Boolean = chat.isLeft

    def uber = chat.fold(u => UberUser(u.id, u.first_name, "user", u.last_name, u.username), g => UberUser(g.id, g.title, "group", None, None))
  }
}
