package de.chrgroth.james.platform.domain.model.user

import java.time.Instant

@JvmInline
value class Username(val value: String)

data class User(
  val username: Username,
  val passwordHash: String,
  val roles: Set<UserRole>,
  val createdAt: Instant,
  val lastLoginAt: Instant? = null,
)

enum class UserRole {
  USER,
  DEVELOPER,
  ADMIN,
}
