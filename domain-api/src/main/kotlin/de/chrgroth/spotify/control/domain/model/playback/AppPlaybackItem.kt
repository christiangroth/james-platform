package de.chrgroth.spotify.control.domain.model.playback

import de.chrgroth.spotify.control.domain.model.user.UserId
import kotlin.time.Instant

data class AppPlaybackItem(
  val userId: UserId,
  val playedAt: Instant,
  val trackId: String,
  val secondsPlayed: Long,
)
