package de.chrgroth.james.platform.domain.model.playback

import de.chrgroth.james.platform.domain.model.user.UserId
import kotlin.time.Instant

data class AppPlaybackItem(
  val userId: UserId,
  val playedAt: Instant,
  val trackId: String,
  val secondsPlayed: Long,
)
