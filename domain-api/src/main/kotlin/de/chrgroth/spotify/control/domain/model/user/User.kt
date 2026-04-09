package de.chrgroth.spotify.control.domain.model.user

import kotlin.time.Instant

@JvmInline
value class UserId(val value: String)

data class User(
  val spotifyUserId: UserId,
  val displayName: String,
  val encryptedAccessToken: String,
  val encryptedRefreshToken: String,
  val tokenExpiresAt: Instant,
  val lastLoginAt: Instant,
)
