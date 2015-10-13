package io.github.morgaroth.telegram.bot.core.api.models.extractors

import io.github.morgaroth.telegram.bot.core.api.models._
import io.github.morgaroth.telegram.bot.core.engine.NewUpdate

//object NewChatParticipantMessage {
//  def unapply(m: Message) = m match {
//    case _ => None
//  }
//}

object NewChatParticipant {
  def unapply(u: NewUpdate): Option[(User, (Chat, User, Int))] = {
    u match {
      case NewUpdate(_, _, Update(_, m)) if m.new_chat_participant.isDefined =>
        Some(m.new_chat_participant.get, (m.chat, m.from, m.message_id))
      case _ => None
    }
  }
}

object RemovedParticipant {
  def unapply(u: NewUpdate): Option[(User, Long, (Chat, User, Int))] = {
    u match {
      case NewUpdate(_, _, Update(_, m)) if m.left_chat_participant.isDefined =>
        Some(m.left_chat_participant.get, m.date, (m.chat, m.from, m.message_id))
      case _ => None
    }
  }
}
