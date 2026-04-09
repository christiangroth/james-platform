package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogPort
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackAggregationPort
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackPort
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistPort
import de.chrgroth.spotify.control.domain.port.`in`.user.UserProfilePort
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DomainOutboxContractTests {

  private val allEvents: List<DomainOutboxEvent> = listOf(
    DomainOutboxEvent.FetchPlaybackData(UserId("user-1")),
    DomainOutboxEvent.UpdateUserProfile(UserId("user-1")),
    DomainOutboxEvent.SyncPlaylistInfo(UserId("user-1")),
    DomainOutboxEvent.SyncPlaylistData(UserId("user-1"), "playlist-1"),
    DomainOutboxEvent.SyncPlaylistData(UserId("user-1"), "playlist-1", "https://api.spotify.com/v1/playlists/playlist-1/tracks?offset=50&limit=50", "snapshot-abc"),
    DomainOutboxEvent.RebuildPlaybackData(UserId("user-1")),
    DomainOutboxEvent.AppendPlaybackData(UserId("user-1")),
    DomainOutboxEvent.SyncArtistDetails("artist-1", UserId("user-1")),
    DomainOutboxEvent.SyncArtistAlbums("artist-1", UserId("user-1")),
    DomainOutboxEvent.SyncAlbumDetails("album-1"),
    DomainOutboxEvent.AggregatePlaybackData(UserId("user-1"), AggregationPeriodType.DAY, LocalDate(2024, 1, 15)),
    DomainOutboxEvent.AggregatePlaybackData(UserId("user-1"), AggregationPeriodType.WEEK, LocalDate(2024, 1, 8)),
    DomainOutboxEvent.AggregatePlaybackData(UserId("user-1"), AggregationPeriodType.MONTH, LocalDate(2024, 1, 1)),
    DomainOutboxEvent.AggregatePlaybackData(UserId("user-1"), AggregationPeriodType.QUARTER, LocalDate(2024, 1, 1)),
    DomainOutboxEvent.AggregatePlaybackData(UserId("user-1"), AggregationPeriodType.YEAR, LocalDate(2024, 1, 1)),
  )

  @Test
  fun `every DomainOutboxEvent returns a non-blank deduplication key`() {
    allEvents.forEach { event ->
      assertThat(event.deduplicationKey)
        .describedAs("deduplicationKey for ${event::class.simpleName}")
        .isNotBlank()
    }
  }

  @Test
  fun `deduplication key includes userId to allow per-user deduplication`() {
    val userId = "user-abc"
    listOf(
      DomainOutboxEvent.FetchPlaybackData(UserId(userId)),
      DomainOutboxEvent.UpdateUserProfile(UserId(userId)),
      DomainOutboxEvent.SyncPlaylistInfo(UserId(userId)),
      DomainOutboxEvent.SyncPlaylistData(UserId(userId), "playlist-abc"),
      DomainOutboxEvent.RebuildPlaybackData(UserId(userId)),
      DomainOutboxEvent.AppendPlaybackData(UserId(userId)),
      DomainOutboxEvent.AggregatePlaybackData(UserId(userId), AggregationPeriodType.DAY, LocalDate(2024, 1, 15)),
    ).forEach { event ->
      assertThat(event.deduplicationKey)
        .describedAs("deduplicationKey for ${event::class.simpleName} should contain userId")
        .contains(userId)
    }
  }

  @Test
  fun `payload round-trip restores original event`() {
    allEvents.forEach { event ->
      val restored = DomainOutboxEvent.fromKey(event.key, event.serializePayload)
      assertThat(restored)
        .describedAs("round-trip for ${event::class.simpleName}")
        .isEqualTo(event)
    }
  }

  @Test
  fun `every DomainOutboxEvent type has a handler method in one of the domain ports`() {
    val allPortMethods = listOf(PlaybackPort::class, PlaybackAggregationPort::class, CatalogPort::class, PlaylistPort::class, UserProfilePort::class)
      .flatMap { it.java.methods.toList() }
    allEvents.forEach { event ->
      val eventClass = event::class.java
      val hasMatchingHandle = allPortMethods.any { method ->
        method.name == "handle" && method.parameterCount == 1 && method.parameterTypes[0].isAssignableFrom(eventClass)
      }
      assertThat(hasMatchingHandle)
        .describedAs("One of the domain ports should have method 'handle(${eventClass.simpleName})'")
        .isTrue()
    }
  }
}
