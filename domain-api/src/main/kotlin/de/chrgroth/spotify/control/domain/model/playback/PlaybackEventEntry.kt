package de.chrgroth.spotify.control.domain.model.playback

import kotlin.time.Instant

data class PlaybackEventEntry(
  val type: PlaybackEventType,
  val timestamp: Instant,
  val json: String,
  val isWarning: Boolean,
)
