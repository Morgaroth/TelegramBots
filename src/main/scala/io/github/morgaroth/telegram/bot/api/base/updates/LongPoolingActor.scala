package io.github.morgaroth.telegram.bot.api.base.updates

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import io.github.morgaroth.telegram.bot.api.base.methods._
import spray.http.StatusCodes
import spray.httpx.UnsuccessfulResponseException

/**
 * Created by mateusz on 18.09.15.
 */
object LongPoolingActor {
  def props(botToken: String) = Props(classOf[LongPoolingActor], botToken)
}

class LongPoolingActor(val botToken: String) extends Actor with ActorLogging with Methods {

  import context.dispatcher

  getMe().onComplete(x => println(s"get me result $x"))
  private val eventualResponse = getUpdates(GetUpdatesReq())
  eventualResponse.onSuccess { case updates => log.info(s"updates: $updates") }
  eventualResponse.onFailure {
    case ex: UnsuccessfulResponseException if ex.response.status == StatusCodes.Conflict =>
      log.warning(s"webhook is set, unsetting...")
      unsetWebHook().onComplete(x => log.info(s"unsetting webhook end with $x"))
    case x => x.printStackTrace()
  }

  //  setWebHook(SetWebHookReq("https://example.com/sdfdsfasd/callback", Some(new File("certificate"))).toMultipartFormData)

  override def receive: Receive = {
    case _ =>
  }

  override def actorSystem: ActorSystem = context.system
}
