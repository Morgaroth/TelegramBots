package io.github.morgaroth.telegram.bot.core.api.models


case class UberUser(id: Int,
                    firstName: String,
                    kind: String,
                    lastName: Option[String] = None,
                    username: Option[String] = None
                     )
