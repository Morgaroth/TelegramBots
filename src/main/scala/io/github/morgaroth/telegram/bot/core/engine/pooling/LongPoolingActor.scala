package io.github.morgaroth.telegram.bot.core.engine.pooling

import akka.actor._
import io.github.morgaroth.telegram.bot.core.api.methods.{GetUpdatesReq, Methods, Response}
import io.github.morgaroth.telegram.bot.core.api.models.Update
import spray.http.StatusCodes
import spray.httpx.UnsuccessfulResponseException

import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps, reflectiveCalls}
import scala.util.{Failure, Success}

/**
 * Created by mateusz on 18.09.15.
 */
object LongPoolingActor {

  private[LongPoolingActor] case object Poll

  def props(botActor: ActorRef, botName: String, botToken: String) =
    Props(classOf[LongPoolingActor], botActor, botName, botToken)

  implicit def wrapToMaxoption[A](tr: TraversableOnce[A]): Object {def maxOpt: Option[A]} = new {
    def maxOpt = if (tr.isEmpty) None else Some(tr.max)
  }
}

class LongPoolingActor(botActor: ActorRef, botName: String, val botToken: String) extends Actor with ActorLogging with Methods {

  import LongPoolingActor._
  import context.dispatcher

  // unsetWebHook(SetWebHookReq.unset).onComplete(x => log.info(s"unsetting webhook end with $x"))

  val hardSelf = self
  var offset: Option[Int] = None

  def dispatchPoll(): Unit = {
    hardSelf ! Poll
  }

  dispatchPoll()

  override def receive: Receive = {
    case Poll =>
      log.info(s"dispatching poll to bot $botName")
      getUpdates(GetUpdatesReq(offset, 10, 10 seconds)).onComplete(hardSelf ! _)

    case Success(r@Response(true, Right(updates: List[Update]), _)) =>
      log.info(s"for bot $botName received updates $updates")
      val nextId = updates.map(_.update_id).maxOpt.map(_ + 1)
      //      offset = nextId
      updates.sortBy(_.update_id).foreach(botActor ! _)
      dispatchPoll()

    case Success(response: Response[List[Update]]) =>
      log.warning(s"for bot $botName another response $response")
      dispatchPoll()

    case Failure(ex: UnsuccessfulResponseException) if ex.response.status == StatusCodes.Conflict =>
      log.warning(s"got bot $botName is configured webhook .. :/ ${ex.getMessage}")

    case Failure(ex) =>
      log.error(ex, s"pooling bot $botName end with exception")
      dispatchPoll()
  }

  override def actorSystem: ActorSystem = context.system
}
