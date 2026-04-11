package de.chrgroth.james.platform.domain.model.playback

import de.chrgroth.james.platform.domain.model.catalog.AlbumId
import de.chrgroth.james.platform.domain.model.catalog.ArtistId
import de.chrgroth.james.platform.domain.model.catalog.TrackId
import de.chrgroth.james.platform.domain.model.user.UserId
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
