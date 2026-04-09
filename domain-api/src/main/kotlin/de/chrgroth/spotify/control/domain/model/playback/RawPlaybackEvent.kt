package de.chrgroth.spotify.control.domain.model.playback

import kotlin.time.Instant

data class RawPlaybackEvent(
  val timestamp: Instant,
  val json: String,
)
