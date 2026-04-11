package de.chrgroth.james.platform.domain.model.catalog

data class TrackBrowseItem(
  val trackId: String,
  val trackNumber: Int?,
  val discNumber: Int?,
  val title: String,
  val durationMs: Long,
)
