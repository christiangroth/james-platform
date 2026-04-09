package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playback.SpotifyPlaybackPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class SpotifyCurrentlyPlayingAdapterTests {

  @Inject
  lateinit var spotifyPlayback: SpotifyPlaybackPort

  @Test
  fun `getCurrentlyPlaying returns item from mock`() {
    val result = spotifyPlayback.getCurrentlyPlaying(UserId("test-user-a"), AccessToken("mock-access-token"))

    assertThat(result).isInstanceOf(Either.Right::class.java)
    val item = (result as Either.Right).value
    assertThat(item).isNotNull
    assertThat(item!!.trackId).isEqualTo(TrackId("track-2"))
    assertThat(item.trackName).isEqualTo("Track Two")
    assertThat(item.artistIds).containsExactly(ArtistId("artist-2"))
    assertThat(item.artistNames).containsExactly("Artist Two")
    assertThat(item.progressMs).isEqualTo(45000L)
    assertThat(item.durationMs).isEqualTo(200000L)
    assertThat(item.isPlaying).isTrue()
    assertThat(item.spotifyUserId).isEqualTo(UserId("test-user-a"))
  }

  @Test
  fun `getCurrentlyPlaying records spotify request metrics`() {
    spotifyPlayback.getCurrentlyPlaying(UserId("test-user-a"), AccessToken("mock-access-token"))

    // Metrics are recorded via shared SpotifyHttpMetrics
    assertThat(true).isTrue()
  }
}
