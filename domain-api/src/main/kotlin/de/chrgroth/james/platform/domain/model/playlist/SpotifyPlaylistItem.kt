package de.chrgroth.james.platform.domain.model.playlist

data class SpotifyPlaylistItem(
  val id: String,
  val name: String,
  val snapshotId: String,
  val ownerId: String,
)
