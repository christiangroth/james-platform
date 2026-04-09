package de.chrgroth.spotify.control.domain.model.playlist

import kotlin.time.Instant

data class AppPlaylistCheck(
  val checkId: String,
  val playlistId: PlaylistId,
  val lastCheck: Instant,
  val succeeded: Boolean,
  val violations: List<String>,
)
