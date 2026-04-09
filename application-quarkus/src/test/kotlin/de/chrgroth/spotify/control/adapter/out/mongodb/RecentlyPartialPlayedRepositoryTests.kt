package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPartialPlayedItem
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPartialPlayedRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class RecentlyPartialPlayedRepositoryTests {

  @Inject
  lateinit var recentlyPartialPlayedRepository: RecentlyPartialPlayedRepositoryPort

  private val userId = UserId("test-${UUID.randomUUID()}")
  private val now = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }

  private fun item(index: Int, trackSuffix: String = "$index") = RecentlyPartialPlayedItem(
    spotifyUserId = userId,
    trackId = TrackId("track-$trackSuffix"),
    trackName = "Track $trackSuffix",
    artistIds = listOf(ArtistId("artist-id-$index")),
    artistNames = listOf("Artist $index"),
    playedAt = now - index.hours,
    startTime = now - index.hours - (index * 30).seconds,
    playedSeconds = (index * 30).toLong(),
  )

  @Test
  fun `findByUserIdAndTrackIds returns matching items`() {
    val item1 = item(1, "a")
    val item2 = item(2, "b")
    val item3 = item(3, "c")
    recentlyPartialPlayedRepository.saveAll(listOf(item1, item2, item3))

    val result = recentlyPartialPlayedRepository.findByUserIdAndTrackIds(
      userId,
      setOf(TrackId("track-a"), TrackId("track-b")),
    )

    assertThat(result.map { it.trackId }).containsExactlyInAnyOrder(TrackId("track-a"), TrackId("track-b"))
  }

  @Test
  fun `findByUserIdAndTrackIds returns empty list for empty input`() {
    recentlyPartialPlayedRepository.saveAll(listOf(item(1)))

    val result = recentlyPartialPlayedRepository.findByUserIdAndTrackIds(userId, emptySet())

    assertThat(result).isEmpty()
  }

  @Test
  fun `findByUserIdAndTrackIds returns empty list when no match`() {
    recentlyPartialPlayedRepository.saveAll(listOf(item(1, "x")))

    val result = recentlyPartialPlayedRepository.findByUserIdAndTrackIds(userId, setOf(TrackId("track-unknown")))

    assertThat(result).isEmpty()
  }

  @Test
  fun `deleteByPlayedAts removes only matching items`() {
    val item1 = item(1, "del-a")
    val item2 = item(2, "del-b")
    val item3 = item(3, "del-c")
    recentlyPartialPlayedRepository.saveAll(listOf(item1, item2, item3))

    recentlyPartialPlayedRepository.deleteByPlayedAts(userId, setOf(item1.playedAt, item2.playedAt))

    val remaining = recentlyPartialPlayedRepository.findExistingPlayedAts(
      userId,
      setOf(item1.playedAt, item2.playedAt, item3.playedAt),
    )
    assertThat(remaining).containsOnly(item3.playedAt)
  }

  @Test
  fun `deleteByPlayedAts is a no-op for empty input`() {
    val item1 = item(1, "noop-a")
    recentlyPartialPlayedRepository.saveAll(listOf(item1))

    recentlyPartialPlayedRepository.deleteByPlayedAts(userId, emptySet())

    val remaining = recentlyPartialPlayedRepository.findExistingPlayedAts(userId, setOf(item1.playedAt))
    assertThat(remaining).containsOnly(item1.playedAt)
  }

  @Test
  fun `deleteByPlayedAts does not affect other users`() {
    val otherUserId = UserId("other-${UUID.randomUUID()}")
    val myItem = RecentlyPartialPlayedItem(
      spotifyUserId = userId,
      trackId = TrackId("track-shared"),
      trackName = "Track Shared",
      artistIds = listOf(ArtistId("artist-1")),
      artistNames = listOf("Artist 1"),
      playedAt = now - 1.hours,
      startTime = now - 1.hours - 60.seconds,
      playedSeconds = 60L,
    )
    val otherItem = RecentlyPartialPlayedItem(
      spotifyUserId = otherUserId,
      trackId = TrackId("track-shared"),
      trackName = "Track Shared",
      artistIds = listOf(ArtistId("artist-1")),
      artistNames = listOf("Artist 1"),
      playedAt = now - 1.hours - 5.minutes,
      startTime = now - 1.hours - 5.minutes - 60.seconds,
      playedSeconds = 60L,
    )
    recentlyPartialPlayedRepository.saveAll(listOf(myItem, otherItem))

    recentlyPartialPlayedRepository.deleteByPlayedAts(userId, setOf(myItem.playedAt))

    val myRemaining = recentlyPartialPlayedRepository.findExistingPlayedAts(userId, setOf(myItem.playedAt))
    assertThat(myRemaining).isEmpty()
    val otherRemaining = recentlyPartialPlayedRepository.findExistingPlayedAts(otherUserId, setOf(otherItem.playedAt))
    assertThat(otherRemaining).containsOnly(otherItem.playedAt)
  }
}
