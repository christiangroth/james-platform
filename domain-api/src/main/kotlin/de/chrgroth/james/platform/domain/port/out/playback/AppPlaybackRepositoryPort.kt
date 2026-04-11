package de.chrgroth.james.platform.domain.port.out.playback

import de.chrgroth.james.platform.domain.model.playback.AppPlaybackItem
import de.chrgroth.james.platform.domain.model.user.UserId
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

interface AppPlaybackRepositoryPort {
  fun saveAll(items: List<AppPlaybackItem>)
  fun deleteAllByUserId(userId: UserId)
  fun deleteAllByTrackIds(trackIds: Set<String>)
  fun deleteByUserAndPlayedAts(userId: UserId, playedAts: Set<Instant>)
  fun findMostRecentPlayedAt(userId: UserId): Instant?
  fun findOldestPlayedAt(): Instant?
  fun findExistingPlayedAts(userId: UserId, playedAts: Set<Instant>): Set<Instant>
  fun countAll(userId: UserId): Long
  fun countSince(userId: UserId, since: Instant): Long
  fun countPerDaySince(userId: UserId, since: Instant): List<Pair<LocalDate, Long>>
  fun findRecentlyPlayed(userId: UserId, limit: Int): List<AppPlaybackItem>
  fun findAllSince(userId: UserId, since: Instant): List<AppPlaybackItem>
  fun findAllBetween(userId: UserId, from: Instant, to: Instant): List<AppPlaybackItem>
  fun sumSecondsPlayedByTrackIdSince(userId: UserId, since: Instant): Map<String, Long>
  fun findAllDistinctTrackIds(): Set<String>
}
