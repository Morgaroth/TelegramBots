package io.github.morgaroth.telegram.bot.bots.groupalias

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, Props}
import com.typesafe.config.Config
import io.github.morgaroth.telegram.bot.core.api.models.SendMessage
import io.github.morgaroth.telegram.bot.core.api.models.extractors.{NoArgCommand, MultiArgCommand, SingleArgCommand}
import io.github.morgaroth.utils.mongodb.salat.MongoDAO
import org.bson.types.ObjectId
import com.novus.salat.global.ctx
import scala.language.reflectiveCalls

/**
 * Created by mateusz on 22.10.15.
 */

case class GroupAlias(
                       chat: Int,
                       group: String,
                       members: Set[String] = Set.empty,
                       _id: Option[ObjectId]
                       )

trait GroupAliasDao {
  def config: Config

  def collection: Option[String] = None

  lazy val dao = {
    val d = new MongoDAO[GroupAlias](config, collection.getOrElse("groups")) {}
    //places for indexes
    //    d.collection.ensureIndex("hash")
    d
  }
}

object GroupAliasBot {

  def props() = Props[GroupAliasBot]
}

class GroupAliasBot extends Actor with ActorLogging {

  import context.dispatcher

  override def receive: Receive = {
    case MultiArgCommand("addMember", args, (chat, from, _)) =>
    //nothing

    case NoArgCommand("start", (ch, _, _)) =>
      sender() ! SendMessage(ch.chatId, "Hello, this bot is under implementation")

  }


}
