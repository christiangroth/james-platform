package de.chrgroth.spotify.control.domain.port.out.playback

import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPartialPlayedItem
import de.chrgroth.spotify.control.domain.model.user.UserId
import kotlin.time.Instant

interface RecentlyPartialPlayedRepositoryPort {
  fun findExistingPlayedAts(userId: UserId, playedAts: Set<Instant>): Set<Instant>
  fun findSince(userId: UserId, since: Instant?): List<RecentlyPartialPlayedItem>
  fun findByUserIdAndTrackIds(userId: UserId, trackIds: Set<TrackId>): List<RecentlyPartialPlayedItem>
  fun saveAll(items: List<RecentlyPartialPlayedItem>)
  fun deleteByPlayedAts(userId: UserId, playedAts: Set<Instant>)
}
