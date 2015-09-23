package io.github.morgaroth.telegram.bot.core.engine.caching

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import com.typesafe.config.{Config, ConfigFactory}
import io.github.morgaroth.telegram.bot.core.engine._
import io.github.morgaroth.telegram.bot.core.engine.caching.CacheActor.CleanOld
import net.ceedubs.ficus.Ficus._
import org.joda.time.DateTime

import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * Created by mateusz on 21.09.15.
 */
object CacheActor {

  private[CacheActor] case object CleanOld

  def RAMProps(retention: FiniteDuration) = Props(classOf[RAMCacheActor], retention)

  def DBProps(dbCfg: Config): Props = Props(classOf[DBCacheActor], dbCfg)

  def DBProps(mongoUri: String, collectionName: String): Props = DBProps(mongoUri, Some(collectionName), None)

  def DBProps(mongoUri: String, retention: FiniteDuration): Props = DBProps(mongoUri, None, Some(retention))

  def DBProps(mongoUri: String, collectionName: String, retention: FiniteDuration): Props = DBProps(mongoUri, Some(collectionName), Some(retention))

  def DBProps(mongoUri: String, collectionName: Option[String], retention: Option[FiniteDuration]): Props = {
    val collectionNameEntry = collectionName.map(x => s", name=$x, ").getOrElse("")
    val ret = retention.map(x => s", retention=${x.toSeconds} s, ").getOrElse("")
    Props(classOf[DBCacheActor], ConfigFactory.parseString( s"""uri=$mongoUri $collectionNameEntry  $ret"""))
  }
}

trait CacheActor extends Actor with ActorLogging {
  def updatesRetention: FiniteDuration

  implicit val dispatcher = context.dispatcher

  def barrier = DateTime.now().minus(updatesRetention.toMillis)

  context.system.scheduler.schedule(1 minute, 1 minute, self, CleanOld)

  override def receive: Receive = {
    case CacheUpdate(update) =>
      val s = sender()
      persistUpdate(update).onComplete {
        case Success(_) => s ! OK(update.id)
        case Failure(t) => s ! Fail(update.id, t)
      }

    case UpdateHandled(id) =>
      val s = sender()
      markHandled(id).onComplete {
        case Success(_) => s ! OK(id)
        case Failure(t) => s ! Fail(id, t)
      }

    case GetRemaining(botId) =>
      val s = sender()
      getRemaining(botId).onComplete {
        case Success(remaining) => s ! Remaining(remaining)
        case Failure(t) => s ! GetRemainingFail(botId, t)
      }

    case CleanOld =>
      cleanOld()
  }

  def cleanOld(): Future[Unit]

  def persistUpdate(update: NewUpdate): Future[Unit]

  def markHandled(id: UUID): Future[Unit]

  def getRemaining(botId: String): Future[List[NewUpdate]]
}

class RAMCacheActor(val updatesRetention: FiniteDuration) extends CacheActor {

  val updates: MutableMap[UUID, UpdateRecord] = MutableMap.empty

  override def persistUpdate(update: NewUpdate): Future[Unit] = {
    updates.update(update.id, UpdateRecord(update))
    Future.successful()
  }

  override def markHandled(id: UUID): Future[Unit] = {
    updates - id
    Future.successful()
  }

  override def getRemaining(botId: String): Future[List[NewUpdate]] = {
    Future.successful(updates.values.filter(_.update.botId == botId).map(_.update).toList)
  }

  override def cleanOld(): Future[Unit] = {
    Future.successful(updates.filter(_._2.inserted.isBefore(barrier)))
  }
}

class DBCacheActor(dbConfig: Config) extends CacheActor {

  override def updatesRetention: FiniteDuration = dbConfig.as[Option[FiniteDuration]]("retention").getOrElse(1 day)

  val dao = new NewUpdateDBBaseDao {
    override def cfg: Config = dbConfig
  }

  override def persistUpdate(update: NewUpdate): Future[Unit] = Future(dao.save(update))

  override def getRemaining(botId: String): Future[List[NewUpdate]] = Future(dao.getRemaining(botId))

  override def markHandled(id: UUID): Future[Unit] = Future(dao.remove(id))

  override def cleanOld(): Future[Unit] = Future(dao.dropOld(barrier))
}