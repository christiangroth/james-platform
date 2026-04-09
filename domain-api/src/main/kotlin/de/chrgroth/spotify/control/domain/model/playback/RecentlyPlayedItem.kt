package de.chrgroth.spotify.control.domain.model.playback

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import kotlin.time.Instant

data class RecentlyPlayedItem(
  val spotifyUserId: UserId,
  val trackId: TrackId,
  val trackName: String,
  val artistIds: List<ArtistId>,
  val artistNames: List<String>,
  val playedAt: Instant,
  val startTime: Instant? = null,
  val albumId: AlbumId? = null,
  val albumName: String? = null,
  val imageLink: String? = null,
  val durationSeconds: Long? = null,
)
