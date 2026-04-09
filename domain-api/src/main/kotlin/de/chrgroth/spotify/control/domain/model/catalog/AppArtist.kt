package de.chrgroth.spotify.control.domain.model.catalog

import kotlin.time.Instant

/**
 * Processed artist metadata, shared across all users to avoid duplication in app_track.
 * All fields are populated by the Spotify API sync. Artists are never stored partially.
 */
data class AppArtist(
  val id: ArtistId,
  val artistName: String,
  val imageLink: String? = null,
  val type: String? = null,
  val lastSync: Instant,
)
