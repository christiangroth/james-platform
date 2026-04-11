package de.chrgroth.james.platform.domain.port.out.playback

import de.chrgroth.james.platform.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.james.platform.domain.model.user.UserId
import kotlin.time.Instant

interface RecentlyPlayedRepositoryPort {
  fun findExistingPlayedAts(spotifyUserId: UserId, playedAts: Set<Instant>): Set<Instant>
  fun findMostRecentPlayedAt(spotifyUserId: UserId): Instant?
  fun findSince(spotifyUserId: UserId, since: Instant?): List<RecentlyPlayedItem>
  fun saveAll(items: List<RecentlyPlayedItem>)
  fun deleteNonTracks(): Long
}
