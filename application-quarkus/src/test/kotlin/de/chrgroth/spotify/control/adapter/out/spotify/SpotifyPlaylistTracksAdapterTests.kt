package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.infra.OutgoingRequestStatsPort
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class SpotifyPlaylistTracksAdapterTests {

  @Inject
  lateinit var spotifyPlaylist: SpotifyPlaylistPort

  @Inject
  lateinit var outgoingRequestStats: OutgoingRequestStatsPort

  @Inject
  lateinit var meterRegistry: MeterRegistry

  @Test
  fun `getPlaylistTracks returns tracks from mock`() {
    val result = spotifyPlaylist.getPlaylistTracks(UserId("test-user-a"), AccessToken("mock-access-token"), "mock-playlist-1")

    assertThat(result).isInstanceOf(Either.Right::class.java)
    val playlist = (result as Either.Right).value
    assertThat(playlist.spotifyPlaylistId).isEqualTo("mock-playlist-1")
    assertThat(playlist.tracks).hasSize(1)
    assertThat(playlist.tracks[0].trackId).isEqualTo(TrackId("track-1"))
    assertThat(playlist.tracks[0].artistIds).containsExactly(ArtistId("artist-1"))
    assertThat(playlist.tracks[0].albumId).isEqualTo(AlbumId("album-1"))
  }

  @Test
  fun `getPlaylistTracks filters out non-track items`() {
    val result = spotifyPlaylist.getPlaylistTracks(UserId("test-user-a"), AccessToken("mock-access-token"), "mock-playlist-1")

    assertThat(result).isInstanceOf(Either.Right::class.java)
    val playlist = (result as Either.Right).value
    assertThat(playlist.tracks.none { it.trackId == TrackId("episode-1") }).isTrue
  }

  @Test
  fun `getPlaylistTracks filters out null track items`() {
    val result = spotifyPlaylist.getPlaylistTracks(UserId("test-user-a"), AccessToken("mock-access-token"), "mock-playlist-1")

    assertThat(result).isInstanceOf(Either.Right::class.java)
    val playlist = (result as Either.Right).value
    assertThat(playlist.tracks).hasSize(1)
  }

  @Test
  fun `getPlaylistTracks handles null items in the items list`() {
    val result = spotifyPlaylist.getPlaylistTracks(UserId("test-user-a"), AccessToken("mock-access-token"), "mock-playlist-1")

    assertThat(result).isInstanceOf(Either.Right::class.java)
    val playlist = (result as Either.Right).value
    assertThat(playlist.tracks).hasSize(1)
  }

  @Test
  fun `getPlaylistTracks includes track with null album id`() {
    val result = spotifyPlaylist.getPlaylistTracks(UserId("test-user-a"), AccessToken("mock-access-token"), "mock-playlist-2")

    assertThat(result).isInstanceOf(Either.Right::class.java)
    val playlist = (result as Either.Right).value
    val trackWithNullAlbum = playlist.tracks.find { it.trackId == TrackId("track-3") }
    assertThat(trackWithNullAlbum).isNotNull
    assertThat(trackWithNullAlbum!!.albumId).isNull()
    assertThat(trackWithNullAlbum.artistIds).containsExactly(ArtistId("artist-3"))
  }

  @Test
  fun `getPlaylistTracks includes track with album id when present`() {
    val result = spotifyPlaylist.getPlaylistTracks(UserId("test-user-a"), AccessToken("mock-access-token"), "mock-playlist-2")

    assertThat(result).isInstanceOf(Either.Right::class.java)
    val playlist = (result as Either.Right).value
    val trackWithAlbum = playlist.tracks.find { it.trackId == TrackId("track-1") }
    assertThat(trackWithAlbum).isNotNull
    assertThat(trackWithAlbum!!.albumId).isEqualTo(AlbumId("album-1"))
  }

  @Test
  fun `getPlaylistTracks includes track with null artist id skipping that artist`() {
    val result = spotifyPlaylist.getPlaylistTracks(UserId("test-user-a"), AccessToken("mock-access-token"), "mock-playlist-3")

    assertThat(result).isInstanceOf(Either.Right::class.java)
    val playlist = (result as Either.Right).value
    val trackWithNullArtist = playlist.tracks.find { it.trackId == TrackId("track-4") }
    assertThat(trackWithNullArtist).isNotNull
    assertThat(trackWithNullArtist!!.artistIds).containsExactly(ArtistId("artist-4"))
  }

  @Test
  fun `getPlaylistTracks includes track when only non-null artists are returned`() {
    val result = spotifyPlaylist.getPlaylistTracks(UserId("test-user-a"), AccessToken("mock-access-token"), "mock-playlist-3")

    assertThat(result).isInstanceOf(Either.Right::class.java)
    val playlist = (result as Either.Right).value
    assertThat(playlist.tracks.any { it.trackId == TrackId("track-4") }).isTrue
  }

  @Test
  fun `getPlaylistTracks records spotify request metrics`() {
    spotifyPlaylist.getPlaylistTracks(UserId("test-user-a"), AccessToken("mock-access-token"), "mock-playlist-1")

    val timer = meterRegistry.find("spotify.request").timer()
    assertThat(timer).isNotNull
    assertThat(timer!!.count()).isGreaterThan(0)
  }

  @Test
  fun `getPlaylistTracks increments in-memory request counter`() {
    spotifyPlaylist.getPlaylistTracks(UserId("test-user-a"), AccessToken("mock-access-token"), "mock-playlist-1")

    val stats = outgoingRequestStats.getRequestStats()
    assertThat(stats).isNotEmpty
    assertThat(stats.any { it.requestCountLast24h > 0 }).isTrue
  }
}
