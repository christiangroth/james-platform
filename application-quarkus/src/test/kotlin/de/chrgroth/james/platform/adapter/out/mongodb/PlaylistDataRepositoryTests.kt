package de.chrgroth.james.platform.adapter.out.mongodb

import de.chrgroth.james.platform.domain.model.catalog.AlbumId
import de.chrgroth.james.platform.domain.model.catalog.ArtistId
import de.chrgroth.james.platform.domain.model.playlist.Playlist
import de.chrgroth.james.platform.domain.model.playlist.PlaylistInfo
import de.chrgroth.james.platform.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.james.platform.domain.model.playlist.PlaylistTrack
import de.chrgroth.james.platform.domain.model.catalog.TrackId
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.port.out.playlist.PlaylistRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class PlaylistDataRepositoryTests {

  @Inject
  lateinit var playlistRepository: PlaylistRepositoryPort

  private fun buildPlaylist(playlistId: String) = Playlist(
    spotifyPlaylistId = playlistId,
    tracks = listOf(
      PlaylistTrack(
        trackId = TrackId("track-1"),
        artistIds = listOf(ArtistId("artist-1")),
        albumId = AlbumId("album-1"),
      ),
    ),
  )

  private fun buildPlaylistInfo(playlistId: String, syncStatus: PlaylistSyncStatus = PlaylistSyncStatus.PASSIVE): PlaylistInfo {
    val now = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }
    return PlaylistInfo(
      spotifyPlaylistId = playlistId,
      snapshotId = "snap-1",
      lastSnapshotIdSyncTime = now - 1.hours,
      name = "Playlist $playlistId",
      syncStatus = syncStatus,
    )
  }

  @Test
  fun `findByUserIdAndPlaylistId returns null when no playlist exists`() {
    val userId = UserId("no-playlist-${UUID.randomUUID()}")

    assertThat(playlistRepository.findByUserIdAndPlaylistId(userId, "unknown-playlist")).isNull()
  }

  @Test
  fun `save and findByUserIdAndPlaylistId round-trips playlist correctly`() {
    val userId = UserId("test-${UUID.randomUUID()}")
    val playlist = buildPlaylist("playlist-1")

    playlistRepository.save(userId, playlist)

    val found = playlistRepository.findByUserIdAndPlaylistId(userId, "playlist-1")
    assertThat(found).isNotNull
    assertThat(found!!.spotifyPlaylistId).isEqualTo("playlist-1")
    assertThat(found.tracks).hasSize(1)
    assertThat(found.tracks[0].trackId).isEqualTo(TrackId("track-1"))
    assertThat(found.tracks[0].artistIds).containsExactly(ArtistId("artist-1"))
  }

  @Test
  fun `numberOfTracks returns correct size after save`() {
    val userId = UserId("test-${UUID.randomUUID()}")
    val playlistId = "playlist-ntracks-${UUID.randomUUID()}"
    val playlist = Playlist(
      spotifyPlaylistId = playlistId,
      tracks = listOf(
        PlaylistTrack(trackId = TrackId("t1"), artistIds = listOf(ArtistId("a1")), albumId = AlbumId("al1")),
        PlaylistTrack(trackId = TrackId("t2"), artistIds = listOf(ArtistId("a2")), albumId = AlbumId("al2")),
      ),
    )

    playlistRepository.save(userId, playlist)

    val found = playlistRepository.findByUserIdAndPlaylistId(userId, playlistId)
    assertThat(found).isNotNull
    assertThat(found!!.numberOfTracks).isEqualTo(2)
  }

  @Test
  fun `numberOfTracks returns correct size after appendTracks`() {
    val userId = UserId("test-${UUID.randomUUID()}")
    val playlistId = "playlist-append-${UUID.randomUUID()}"
    playlistRepository.save(
      userId,
      Playlist(
        spotifyPlaylistId = playlistId,
        tracks = listOf(PlaylistTrack(trackId = TrackId("t1"), artistIds = listOf(ArtistId("a1")), albumId = AlbumId("al1"))),
      ),
    )

    playlistRepository.appendTracks(
      userId,
      playlistId,
      listOf(
        PlaylistTrack(trackId = TrackId("t2"), artistIds = listOf(ArtistId("a2")), albumId = AlbumId("al2")),
        PlaylistTrack(trackId = TrackId("t3"), artistIds = listOf(ArtistId("a3")), albumId = AlbumId("al3")),
      ),
    )

    val found = playlistRepository.findByUserIdAndPlaylistId(userId, playlistId)
    assertThat(found).isNotNull
    assertThat(found!!.numberOfTracks).isEqualTo(3)
  }

  @Test
  fun `save overwrites previous playlist data`() {
    val userId = UserId("test-${UUID.randomUUID()}")
    playlistRepository.save(userId, buildPlaylist("playlist-1"))

    playlistRepository.save(userId, buildPlaylist("playlist-1"))

    val found = playlistRepository.findByUserIdAndPlaylistId(userId, "playlist-1")
    assertThat(found).isNotNull
    assertThat(found!!.tracks).hasSize(1)
  }

  @Test
  fun `save does not affect playlists of other users`() {
    val userId1 = UserId("test-${UUID.randomUUID()}")
    val userId2 = UserId("test-${UUID.randomUUID()}")
    playlistRepository.save(userId1, buildPlaylist("playlist-1"))
    playlistRepository.save(userId2, buildPlaylist("playlist-2"))

    assertThat(playlistRepository.findByUserIdAndPlaylistId(userId1, "playlist-1")).isNotNull
    assertThat(playlistRepository.findByUserIdAndPlaylistId(userId2, "playlist-2")).isNotNull
    assertThat(playlistRepository.findByUserIdAndPlaylistId(userId1, "playlist-2")).isNull()
    assertThat(playlistRepository.findByUserIdAndPlaylistId(userId2, "playlist-1")).isNull()
  }
}
