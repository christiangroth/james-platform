package de.chrgroth.spotify.control.domain.model.user

import java.time.Instant

@JvmInline
value class UserId(val value: String)

data class User(
  val username: String,
  val passwordHash: String,
  val roles: Set<UserRole>,
  val createdAt: Instant,
)
