package de.chrgroth.james.platform.domain.model.catalog

/**
 * Result of a Spotify album API call, containing the synced album and all its tracks.
 */
data class AlbumSyncResult(
  val album: AppAlbum,
  val tracks: List<AppTrack>,
)
