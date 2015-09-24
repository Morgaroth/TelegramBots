package io.github.morgaroth.telegram.bot.core.api.models.extractors

import io.github.morgaroth.telegram.bot.core.api.models.{User, Chat, Message}

/**
 * Created by mateusz on 24.09.15.
 */
object NoArgCommand {
  def unapply(m: Message): Option[(String, (Chat, User, Int))] = {
    m match {
      case OnlyTextMessage(chat, text, author, mId) if text.startsWith("/") && text.length > 1 =>
        val command = text.drop(1).trim
        command.split( """([ \n\t]+)""").toList match {
          case elem :: Nil => Some((elem,(chat,author,mId)))
          case _ => None
        }
      case _ => None
    }
  }
}

object SingleArgCommand {
  def unapply(m: Message): Option[(String, String, (Chat, User, Int))] = {
    m match {
      case OnlyTextMessage(chat, text, author, mId) if text.startsWith("/") && text.length > 1 =>
        val command = text.drop(1).trim
        command.split( """([ \n\t]+)""").toList match {
          case elem :: arg :: Nil => Some((elem, arg,(chat,author,mId)))
          case _ => None
        }
      case _ => None
    }
  }
}


object MultiArgCommand {
  def unapply(m: Message): Option[(String, List[String], (Chat, User, Int))] = {
    m match {
      case OnlyTextMessage(chat, text, author, mId) if text.startsWith("/") && text.length > 1 =>
        val command = text.drop(1).trim
        command.split( """([ \n\t]+)""").toList match {
          case commandName :: arguments => Some((commandName, arguments,(chat,author,mId)))
          case _ => None
        }
      case _ => None
    }
  }
}