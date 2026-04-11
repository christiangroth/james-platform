package de.chrgroth.james.platform.domain.model.playback

import kotlinx.datetime.LocalDate

data class PlaybackEventViewerResult(
  val date: LocalDate,
  val isToday: Boolean,
  val events: List<PlaybackEventEntry>,
)
