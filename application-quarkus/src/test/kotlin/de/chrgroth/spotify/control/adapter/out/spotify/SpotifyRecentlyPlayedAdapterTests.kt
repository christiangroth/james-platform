package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playback.SpotifyPlaybackPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutgoingRequestStatsPort
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class SpotifyRecentlyPlayedAdapterTests {

  @Inject
  lateinit var spotifyPlayback: SpotifyPlaybackPort

  @Inject
  lateinit var outgoingRequestStats: OutgoingRequestStatsPort

  @Inject
  lateinit var meterRegistry: MeterRegistry

  @Test
  fun `getRecentlyPlayed returns items from mock`() {
    val result = spotifyPlayback.getRecentlyPlayed(UserId("test-user-a"), AccessToken("mock-access-token"))

    assertThat(result).isInstanceOf(Either.Right::class.java)
    val items = (result as Either.Right).value
    assertThat(items).hasSize(1)
    assertThat(items[0].trackId).isEqualTo(TrackId("track-1"))
    assertThat(items[0].trackName).isEqualTo("Track One")
    assertThat(items[0].artistIds).containsExactly(ArtistId("artist-1"))
    assertThat(items[0].artistNames).containsExactly("Artist One")
    assertThat(items[0].spotifyUserId).isEqualTo(UserId("test-user-a"))
    assertThat(items[0].durationSeconds).isEqualTo(210L)
  }

  @Test
  fun `getRecentlyPlayed with after parameter returns items from mock`() {
    val after = Instant.parse("2024-01-01T00:00:00Z")
    val result = spotifyPlayback.getRecentlyPlayed(UserId("test-user-a"), AccessToken("mock-access-token"), after)

    assertThat(result).isInstanceOf(Either.Right::class.java)
    val items = (result as Either.Right).value
    assertThat(items).hasSize(1)
    assertThat(items[0].trackId).isEqualTo(TrackId("track-1"))
  }

  @Test
  fun `getRecentlyPlayed filters out podcast episodes`() {
    val result = spotifyPlayback.getRecentlyPlayed(UserId("test-user-a"), AccessToken("mock-access-token"))

    assertThat(result).isInstanceOf(Either.Right::class.java)
    val items = (result as Either.Right).value
    assertThat(items.none { it.trackId == TrackId("episode-1") }).isTrue
  }

  @Test
  fun `getRecentlyPlayed filters out local tracks`() {
    val result = spotifyPlayback.getRecentlyPlayed(UserId("test-user-a"), AccessToken("mock-access-token"))

    assertThat(result).isInstanceOf(Either.Right::class.java)
    val items = (result as Either.Right).value
    assertThat(items.none { it.trackId == TrackId("local-1") }).isTrue
  }

  @Test
  fun `getRecentlyPlayed records spotify request metrics`() {
    spotifyPlayback.getRecentlyPlayed(UserId("test-user-a"), AccessToken("mock-access-token"))

    val timer = meterRegistry.find("spotify.request").timer()
    assertThat(timer).isNotNull
    assertThat(timer!!.count()).isGreaterThan(0)
  }

  @Test
  fun `getRecentlyPlayed increments in-memory request counter`() {
    spotifyPlayback.getRecentlyPlayed(UserId("test-user-a"), AccessToken("mock-access-token"))

    val stats = outgoingRequestStats.getRequestStats()
    assertThat(stats).isNotEmpty
    assertThat(stats.any { it.requestCountLast24h > 0 }).isTrue
  }
}
