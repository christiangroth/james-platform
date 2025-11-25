package de.chrgroth.james.platform.domain.app

import java.util.UUID

@JvmInline
// TODO change to UUID, but it's not serializable
value class AppId(val value: String) {
  companion object {
    operator fun invoke(): AppId = AppId(UUID.randomUUID().toString())
  }
}

fun String.toAppId(): AppId =
  AppId(this)

data class App(
  val id: AppId,
) {
  companion object {

    // TODO run checks in parallel
    operator fun invoke(): App =
      App(
        id = AppId(),
      )

    fun fromEntity(
      id: AppId,
    ): App = App(
      id = id,
    )
  }
}
