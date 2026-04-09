package de.chrgroth.spotify.control.domain.model.playlist

data class PlaylistCheckStats(
  val succeededChecks: Long,
  val totalChecks: Long,
  val allSucceeded: Boolean,
)
