package io.github.morgaroth.telegram.bot.core.engine.core

import scala.concurrent.duration.FiniteDuration

/**
 * Created by mateusz on 22.09.15.
 */
sealed trait CacheType

case class RAMCache(retention: FiniteDuration) extends CacheType

case class MongoCache(retention: FiniteDuration, uri: String, collName: String) extends CacheType

case object NoCache extends CacheType

sealed trait UpdatesSource

object WebHook extends UpdatesSource

object LongPool extends UpdatesSource


case class BotSettings(
                        botName: String,
                        botToken: String,
                        cacheType: CacheType,
                        updatesType: UpdatesSource
                        )
