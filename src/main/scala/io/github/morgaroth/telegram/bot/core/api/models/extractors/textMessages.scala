package io.github.morgaroth.telegram.bot.core.api.models.extractors

import io.github.morgaroth.telegram.bot.core.api.models.{Chat, Message, User}

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

object TextReply {
  def unapply(m: Message) = m match {
    case Message(mId, from, _, chat, None, None, Some(text), None, None, None, None, None, None, None, None, None, None, None, None, None, Some(replied)) =>
      Some((chat, text, from, replied, mId))
    case _ => None
  }
}