package de.chrgroth.james.platform.domain.model.playback

import de.chrgroth.james.platform.domain.model.catalog.AlbumId
import de.chrgroth.james.platform.domain.model.catalog.ArtistId
import de.chrgroth.james.platform.domain.model.catalog.TrackId
import de.chrgroth.james.platform.domain.model.user.UserId
import kotlin.time.Instant

data class CurrentlyPlayingItem(
  val spotifyUserId: UserId,
  val trackId: TrackId,
  val trackName: String,
  val artistIds: List<ArtistId>,
  val artistNames: List<String>,
  val progressMs: Long,
  val durationMs: Long,
  val isPlaying: Boolean,
  val observedAt: Instant,
  val startTime: Instant,
  val albumId: AlbumId? = null,
)
