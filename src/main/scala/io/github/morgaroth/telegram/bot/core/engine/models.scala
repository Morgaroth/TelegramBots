package io.github.morgaroth.telegram.bot.core.engine

import java.util.UUID

import io.github.morgaroth.telegram.bot.core.api.models.Update

/**
 * Created by mateusz on 20.09.15.
 */
case class NewUpdate(id: UUID, botId: String, update: Update)

case class WebHookSettings(domain: String, bindPort: Int, certificate: Option[java.io.File])

case class CacheUpdate(u: NewUpdate)

case class UpdateHandled(id: UUID)

case class GetRemaining(botId: String)

case class Remaining(remaining: List[NewUpdate])

case class GetRemainingFail(botID: String, exception: Throwable)

case class OK(id: UUID)

case class Fail(id: UUID, exception: Throwable)