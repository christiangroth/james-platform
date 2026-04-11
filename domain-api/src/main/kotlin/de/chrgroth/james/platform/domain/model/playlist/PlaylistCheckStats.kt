package de.chrgroth.james.platform.domain.model.playlist

data class PlaylistCheckStats(
  val succeededChecks: Long,
  val totalChecks: Long,
  val allSucceeded: Boolean,
)
