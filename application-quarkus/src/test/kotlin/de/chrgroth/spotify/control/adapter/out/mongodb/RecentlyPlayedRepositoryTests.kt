package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPlayedRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class RecentlyPlayedRepositoryTests {

  @Inject
  lateinit var recentlyPlayedRepository: RecentlyPlayedRepositoryPort

  private val userId = UserId("test-${UUID.randomUUID()}")
  private val now = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }

  private fun item(index: Int) = RecentlyPlayedItem(
    spotifyUserId = userId,
    trackId = TrackId("track-$index"),
    trackName = "Track $index",
    artistIds = listOf(ArtistId("artist-id-$index")),
    artistNames = listOf("Artist $index"),
    playedAt = now - index.hours,
    durationSeconds = (index * 180).toLong(),
  )

  @Test
  fun `findMostRecentPlayedAt returns most recent playedAt`() {
    recentlyPlayedRepository.saveAll(listOf(item(1), item(2), item(3)))

    val mostRecent = recentlyPlayedRepository.findMostRecentPlayedAt(userId)

    assertThat(mostRecent).isEqualTo(item(1).playedAt)
  }

  @Test
  fun `findMostRecentPlayedAt returns null when no items exist`() {
    val result = recentlyPlayedRepository.findMostRecentPlayedAt(userId)
    assertThat(result).isNull()
  }

  @Test
  fun `saveAll persists items and findExistingPlayedAts returns their playedAt values`() {
    val items = listOf(item(1), item(2))
    recentlyPlayedRepository.saveAll(items)

    val playedAts = items.map { it.playedAt }.toSet()
    val existing = recentlyPlayedRepository.findExistingPlayedAts(userId, playedAts)

    assertThat(existing).containsExactlyInAnyOrderElementsOf(playedAts)
  }

  @Test
  fun `findExistingPlayedAts returns empty set when no items exist`() {
    val playedAts = setOf(now - 10.hours)
    val existing = recentlyPlayedRepository.findExistingPlayedAts(userId, playedAts)
    assertThat(existing).isEmpty()
  }

  @Test
  fun `findExistingPlayedAts returns empty set for empty input`() {
    val existing = recentlyPlayedRepository.findExistingPlayedAts(userId, emptySet())
    assertThat(existing).isEmpty()
  }

  @Test
  fun `findExistingPlayedAts only returns playedAts that match`() {
    val savedItem = item(1)
    recentlyPlayedRepository.saveAll(listOf(savedItem))

    val newPlayedAt = now - 5.hours
    val result = recentlyPlayedRepository.findExistingPlayedAts(userId, setOf(savedItem.playedAt, newPlayedAt))

    assertThat(result).containsOnly(savedItem.playedAt)
    assertThat(result).doesNotContain(newPlayedAt)
  }

  private fun nonTrackItem(index: Int) = RecentlyPlayedItem(
    spotifyUserId = userId,
    trackId = TrackId("episode-$index"),
    trackName = "Episode $index",
    artistIds = emptyList(),
    artistNames = emptyList(),
    playedAt = now - index.hours,
  )

  @Test
  fun `deleteNonTracks removes items with empty artistIds`() {
    recentlyPlayedRepository.saveAll(listOf(item(1), nonTrackItem(2), nonTrackItem(3)))

    val deleted = recentlyPlayedRepository.deleteNonTracks()

    assertThat(deleted).isEqualTo(2L)
    val remaining = recentlyPlayedRepository.findExistingPlayedAts(userId, setOf(item(1).playedAt, nonTrackItem(2).playedAt, nonTrackItem(3).playedAt))
    assertThat(remaining).containsOnly(item(1).playedAt)
  }

  @Test
  fun `deleteNonTracks returns zero when no non-track items exist`() {
    recentlyPlayedRepository.saveAll(listOf(item(4), item(5)))

    val deleted = recentlyPlayedRepository.deleteNonTracks()

    assertThat(deleted).isEqualTo(0L)
  }

  @Test
  fun `findSince persists and returns durationSeconds`() {
    val itemWithDuration = item(1)
    recentlyPlayedRepository.saveAll(listOf(itemWithDuration))

    val result = recentlyPlayedRepository.findSince(userId, null)

    assertThat(result).hasSize(1)
    assertThat(result[0].durationSeconds).isEqualTo(itemWithDuration.durationSeconds)
  }

  @Test
  fun `findSince returns zero durationSeconds when not set`() {
    val itemWithoutDuration = RecentlyPlayedItem(
      spotifyUserId = userId,
      trackId = TrackId("track-noduration"),
      trackName = "Track No Duration",
      artistIds = listOf(ArtistId("artist-id-1")),
      artistNames = listOf("Artist 1"),
      playedAt = now - 10.hours,
      durationSeconds = null,
    )
    recentlyPlayedRepository.saveAll(listOf(itemWithoutDuration))

    val result = recentlyPlayedRepository.findSince(userId, null)

    val found = result.first { it.trackId == TrackId("track-noduration") }
    assertThat(found.durationSeconds).isEqualTo(0L)
  }
}
