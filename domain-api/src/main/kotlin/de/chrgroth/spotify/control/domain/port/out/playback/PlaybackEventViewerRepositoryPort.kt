package de.chrgroth.spotify.control.domain.port.out.playback

import de.chrgroth.spotify.control.domain.model.playback.RawPlaybackEvent
import de.chrgroth.spotify.control.domain.model.user.UserId
import kotlin.time.Instant

interface PlaybackEventViewerRepositoryPort {
  fun findRecentlyPlayed(userId: UserId, from: Instant, to: Instant): List<RawPlaybackEvent>
  fun findRecentlyPartialPlayed(userId: UserId, from: Instant, to: Instant): List<RawPlaybackEvent>
  fun findCurrentlyPlaying(userId: UserId, from: Instant, to: Instant): List<RawPlaybackEvent>
}
