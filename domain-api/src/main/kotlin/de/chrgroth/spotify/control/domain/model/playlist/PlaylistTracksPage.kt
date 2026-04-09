package de.chrgroth.spotify.control.domain.model.playlist

data class PlaylistTracksPage(
  val snapshotId: String,
  val tracks: List<PlaylistTrack>,
  val nextUrl: String?,
)
