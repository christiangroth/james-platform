package de.chrgroth.spotify.control.domain.model.playback

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import kotlin.time.Instant

data class RecentlyPartialPlayedItem(
  val spotifyUserId: UserId,
  val trackId: TrackId,
  val trackName: String,
  val artistIds: List<ArtistId>,
  val artistNames: List<String>,
  val playedAt: Instant,
  val startTime: Instant,
  val playedSeconds: Long,
  val albumId: AlbumId? = null,
)
