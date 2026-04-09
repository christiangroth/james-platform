package de.chrgroth.spotify.control.domain.model.catalog

import kotlin.time.Instant

/**
 * Processed track metadata, shared across all users to avoid duplication in app_playback.
 * References to app_artist and app_album by ID for further deduplication.
 * All fields are populated by the Spotify API sync. Tracks are never stored partially.
 */
data class AppTrack(
  val id: TrackId,
  val title: String,
  val albumId: AlbumId? = null,
  val albumName: String? = null,
  val artistId: ArtistId,
  val artistName: String? = null,
  val additionalArtistIds: List<ArtistId> = emptyList(),
  val additionalArtistNames: List<String>? = null,
  val discNumber: Int? = null,
  val durationMs: Long? = null,
  val trackNumber: Int? = null,
  val type: String? = null,
  val lastSync: Instant,
)
