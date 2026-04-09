package de.chrgroth.spotify.control.domain.model.catalog

import kotlin.time.Instant

/**
 * Processed album metadata, shared across all users to avoid duplication in app_track.
 * All fields are populated by the Spotify API sync. Albums are never stored partially.
 */
data class AppAlbum(
  val id: AlbumId,
  val totalTracks: Int? = null,
  val title: String? = null,
  val imageLink: String? = null,
  val releaseDate: String? = null,
  val releaseDatePrecision: String? = null,
  val type: String? = null,
  val artistId: ArtistId? = null,
  val artistName: String? = null,
  val additionalArtistIds: List<ArtistId>? = null,
  val additionalArtistNames: List<String>? = null,
  val lastSync: Instant,
)
