package de.chrgroth.spotify.control.domain.model.playback.aggregation

data class AggregationRankEntry(
  val id: String,
  val name: String,
  val totalSeconds: Long,
)
