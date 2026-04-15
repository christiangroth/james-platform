package de.chrgroth.james.platform.domain.model.app

import java.time.Instant

@JvmInline
value class AppId(val value: String)

@JvmInline
value class AppName(val value: String)

data class App(
  val id: AppId,
  val name: AppName,
  val description: String?,
  val developerId: String,
  val status: AppStatus,
  val createdAt: Instant,
  val updatedAt: Instant,
)

enum class AppStatus {
  ACTIVE,
  INACTIVE,
  DEPRECATED,
}
