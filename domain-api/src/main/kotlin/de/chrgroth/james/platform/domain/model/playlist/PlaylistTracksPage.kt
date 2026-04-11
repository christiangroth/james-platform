package de.chrgroth.james.platform.domain.model.playlist

data class PlaylistTracksPage(
  val snapshotId: String,
  val tracks: List<PlaylistTrack>,
  val nextUrl: String?,
)
