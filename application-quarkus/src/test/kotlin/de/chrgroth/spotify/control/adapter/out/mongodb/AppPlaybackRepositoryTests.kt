package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.playback.AppPlaybackItem
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playback.AppPlaybackRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class AppPlaybackRepositoryTests {

  @Inject
  lateinit var appPlaybackRepository: AppPlaybackRepositoryPort

  private val userId = UserId("test-${UUID.randomUUID()}")
  private val now = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }

  private fun item(index: Int) = AppPlaybackItem(
    userId = userId,
    playedAt = now - index.hours,
    trackId = "track-$index",
    secondsPlayed = (index * 30).toLong(),
  )

  @Test
  fun `saveAll persists items and countAll returns correct count`() {
    appPlaybackRepository.saveAll(listOf(item(1), item(2), item(3)))

    val count = appPlaybackRepository.countAll(userId)

    assertThat(count).isGreaterThanOrEqualTo(3)
  }

  @Test
  fun `findMostRecentPlayedAt returns most recent playedAt`() {
    appPlaybackRepository.saveAll(listOf(item(1), item(2), item(3)))

    val mostRecent = appPlaybackRepository.findMostRecentPlayedAt(userId)

    assertThat(mostRecent).isEqualTo(item(1).playedAt)
  }

  @Test
  fun `findMostRecentPlayedAt returns null when no items exist`() {
    val emptyUserId = UserId("empty-${UUID.randomUUID()}")
    val result = appPlaybackRepository.findMostRecentPlayedAt(emptyUserId)
    assertThat(result).isNull()
  }

  @Test
  fun `findExistingPlayedAts returns existing timestamps`() {
    val items = listOf(item(1), item(2))
    appPlaybackRepository.saveAll(items)

    val playedAts = items.map { it.playedAt }.toSet()
    val existing = appPlaybackRepository.findExistingPlayedAts(userId, playedAts)

    assertThat(existing).containsExactlyInAnyOrderElementsOf(playedAts)
  }

  @Test
  fun `findExistingPlayedAts returns empty set for empty input`() {
    val existing = appPlaybackRepository.findExistingPlayedAts(userId, emptySet())
    assertThat(existing).isEmpty()
  }

  @Test
  fun `deleteAllByTrackIds removes items for specified tracks`() {
    appPlaybackRepository.saveAll(listOf(item(1), item(2), item(3)))

    appPlaybackRepository.deleteAllByTrackIds(setOf("track-1", "track-2"))

    val existing = appPlaybackRepository.findExistingPlayedAts(
      userId,
      setOf(item(1).playedAt, item(2).playedAt, item(3).playedAt),
    )
    assertThat(existing).containsOnly(item(3).playedAt)
  }

  @Test
  fun `deleteByUserAndPlayedAts removes only matching items for that user`() {
    val otherUserId = UserId("other-${UUID.randomUUID()}")
    val myItem1 = item(1)
    val myItem2 = item(2)
    val otherItem = AppPlaybackItem(userId = otherUserId, playedAt = now - 1.hours, trackId = "t", secondsPlayed = 0)
    appPlaybackRepository.saveAll(listOf(myItem1, myItem2))
    appPlaybackRepository.saveAll(listOf(otherItem))

    appPlaybackRepository.deleteByUserAndPlayedAts(userId, setOf(myItem1.playedAt))

    val remaining = appPlaybackRepository.findExistingPlayedAts(userId, setOf(myItem1.playedAt, myItem2.playedAt))
    assertThat(remaining).containsOnly(myItem2.playedAt)
    assertThat(appPlaybackRepository.countAll(otherUserId)).isGreaterThanOrEqualTo(1)
  }

  @Test
  fun `deleteByUserAndPlayedAts is a no-op for empty input`() {
    appPlaybackRepository.saveAll(listOf(item(1)))

    appPlaybackRepository.deleteByUserAndPlayedAts(userId, emptySet())

    assertThat(appPlaybackRepository.countAll(userId)).isGreaterThanOrEqualTo(1)
  }

  @Test
  fun `deleteAllByUserId removes only items for that user`() {
    val otherUserId = UserId("other-${UUID.randomUUID()}")
    appPlaybackRepository.saveAll(listOf(item(1), item(2)))
    appPlaybackRepository.saveAll(listOf(
      AppPlaybackItem(userId = otherUserId, playedAt = now - 1.hours, trackId = "t", secondsPlayed = 0),
    ))

    appPlaybackRepository.deleteAllByUserId(userId)

    assertThat(appPlaybackRepository.countAll(userId)).isZero()
    assertThat(appPlaybackRepository.countAll(otherUserId)).isGreaterThanOrEqualTo(1)
  }

  @Test
  fun `findRecentlyPlayed returns limited results sorted by playedAt descending`() {
    appPlaybackRepository.saveAll(listOf(item(1), item(2), item(3)))

    val result = appPlaybackRepository.findRecentlyPlayed(userId, 2)

    assertThat(result).hasSizeLessThanOrEqualTo(2)
  }

  @Test
  fun `sumSecondsPlayedByTrackIdSince aggregates seconds per track excluding zero values`() {
    val itemWithSeconds = AppPlaybackItem(
      userId = userId,
      playedAt = now - 1.hours,
      trackId = "track-a",
      secondsPlayed = 120L,
    )
    val anotherItemSameTrack = AppPlaybackItem(
      userId = userId,
      playedAt = now - 2.hours,
      trackId = "track-a",
      secondsPlayed = 60L,
    )
    val itemOtherTrack = AppPlaybackItem(
      userId = userId,
      playedAt = now - 3.hours,
      trackId = "track-b",
      secondsPlayed = 90L,
    )
    val itemNoSeconds = AppPlaybackItem(
      userId = userId,
      playedAt = now - 4.hours,
      trackId = "track-c",
      secondsPlayed = 0L,
    )
    appPlaybackRepository.saveAll(listOf(itemWithSeconds, anotherItemSameTrack, itemOtherTrack, itemNoSeconds))

    val since = now - 24.hours
    val result = appPlaybackRepository.sumSecondsPlayedByTrackIdSince(userId, since)

    assertThat(result["track-a"]).isEqualTo(180L)
    assertThat(result["track-b"]).isEqualTo(90L)
    assertThat(result).doesNotContainKey("track-c")
  }
}
