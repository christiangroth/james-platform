package de.chrgroth.james.platform.domain.model.user

import java.time.Instant

@JvmInline
value class UserId(val value: String)

@JvmInline
value class Username(val value: String)

data class User(
  val id: UserId,
  val username: Username,
  val passwordHash: String,
  val roles: Set<UserRole>,
  val createdAt: Instant,
  val lastLoginAt: Instant? = null,
  val active: Boolean = true,
)

enum class UserRole {
  USER,
  DEVELOPER,
  ADMIN,
  MONITORING,
}
