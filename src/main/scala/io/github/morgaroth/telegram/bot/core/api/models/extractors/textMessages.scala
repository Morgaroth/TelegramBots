package io.github.morgaroth.telegram.bot.core.api.models.extractors

import io.github.morgaroth.telegram.bot.core.api.models.{Update, Chat, Message, User}
import io.github.morgaroth.telegram.bot.core.engine.NewUpdate

/**
 * Created by mateusz on 24.09.15.
 */
object OnlyTextMessage {
  def unapply(m: Message) = m match {
    case Message(mId, from, _, chat, None, None, Some(text), None, None, None, None, None, None, None, None, None, None, None, None, None, None) =>
      Some((chat, text, from, mId))
    case _ => None
  }
}

object ForwardedTextMessage {
  def unapply(m: Message) = m match {
    case Message(mId, from, _, chat, Some(forwardAuthor), _, Some(text), None, None, None, None, None, None, None, None, None, None, None, None, None, None) =>
      Some((chat, text, from, forwardAuthor, mId))
    case _ => None
  }
}

object TextReplyMessage {
  def unapply(m: Message) = m match {
    case Message(mId, from, _, chat, None, None, Some(text), None, None, None, None, None, None, None, None, None, None, None, None, None, Some(replied)) =>
      Some((chat, text, from, replied, mId))
    case _ => None
  }
}

object TextReply {
  def unapply(u: NewUpdate): Option[(Message, String, (Chat, User, Int))] = {
    u match {
      case NewUpdate(_, _, Update(_, TextReplyMessage(chat, text, from, replied, mId))) =>
        Some(replied, text, (chat, from, mId))
      case _ => None
    }
  }
}

