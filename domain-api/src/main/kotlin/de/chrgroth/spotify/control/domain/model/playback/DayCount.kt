package de.chrgroth.spotify.control.domain.model.playback

import kotlinx.datetime.LocalDate

data class DayCount(
  val date: LocalDate,
  val count: Long,
  val heightPercent: Int,
  val dateLabel: String,
)
