package de.chrgroth.spotify.control.domain.model.playlist

import kotlin.time.Instant

data class PlaylistInfo(
  val spotifyPlaylistId: String,
  val snapshotId: String,
  val lastSnapshotIdSyncTime: Instant,
  val name: String,
  val syncStatus: PlaylistSyncStatus,
  val type: PlaylistType? = null,
  val lastSyncTime: Instant? = null,
)
