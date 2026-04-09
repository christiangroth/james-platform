package de.chrgroth.spotify.control.domain.playlist.check

/*
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.PlaylistFixError
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

class DuplicateTrackIdsCheckRunnerTests {

  private val appTrackRepository: AppTrackRepositoryPort = mockk()
  private val spotifyPlaylist: SpotifyPlaylistPort = mockk()
  private val runner = DuplicateTrackIdsCheckRunner(appTrackRepository, spotifyPlaylist)

  private val userId = UserId("user-1")
  private val accessToken = AccessToken("token")
  private val playlistId = "playlist-1"

  private fun buildTrack(trackId: String) = PlaylistTrack(
    trackId = TrackId(trackId),
    artistIds = listOf(ArtistId("artist-1")),
    albumId = AlbumId("album-1"),
  )

  private fun buildAppTrack(trackId: String, title: String, artistName: String? = "Artist") = AppTrack(
    id = TrackId(trackId),
    title = title,
    artistId = ArtistId("artist-1"),
    artistName = artistName,
    lastSync = Instant.fromEpochMilliseconds(0),
  )

  private fun buildPlaylist(tracks: List<PlaylistTrack>) = Playlist(
    spotifyPlaylistId = playlistId,
    tracks = tracks,
  )

  private fun buildPlaylistInfo(type: PlaylistType? = null) = PlaylistInfo(
    spotifyPlaylistId = playlistId,
    snapshotId = "snap-1",
    lastSnapshotIdSyncTime = Clock.System.now(),
    name = "Playlist $playlistId",
    syncStatus = PlaylistSyncStatus.ACTIVE,
    type = type,
  )

  @Test
  fun `isApplicable returns true for any playlist type`() {
    assertThat(runner.isApplicable(null)).isTrue()
    assertThat(runner.isApplicable(buildPlaylistInfo(PlaylistType.ALL))).isTrue()
    assertThat(runner.isApplicable(buildPlaylistInfo(PlaylistType.YEAR))).isTrue()
    assertThat(runner.isApplicable(buildPlaylistInfo(PlaylistType.UNKNOWN))).isTrue()
  }

  @Test
  fun `run returns no violations for unique tracks`() {
    val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t2")))

    val result = runner.run(userId, playlistId, playlist, null, emptyList())

    assertThat(result.succeeded).isTrue()
    assertThat(result.violations).isEmpty()
    assertThat(result.checkId).isEqualTo("$playlistId:duplicate-track-ids")
  }

  @Test
  fun `run detects duplicate track ids`() {
    val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t1"), buildTrack("t2")))
    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"))) } returns listOf(buildAppTrack("t1", "Song A", "Artist A"))

    val result = runner.run(userId, playlistId, playlist, null, emptyList())

    assertThat(result.succeeded).isFalse()
    assertThat(result.violations).containsExactly("Artist A – Song A")
  }

  @Test
  fun `run reports all duplicate tracks`() {
    val playlist = buildPlaylist(
      listOf(
        buildTrack("t1"),
        buildTrack("t1"),
        buildTrack("t2"),
        buildTrack("t2"),
      ),
    )
    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"), TrackId("t2"))) } returns listOf(
      buildAppTrack("t1", "Song A", "Artist A"),
      buildAppTrack("t2", "Song B", "Artist B"),
    )

    val result = runner.run(userId, playlistId, playlist, null, emptyList())

    assertThat(result.succeeded).isFalse()
    assertThat(result.violations).containsExactlyInAnyOrder("Artist A – Song A", "Artist B – Song B")
  }

  @Test
  fun `run falls back to unknown artist when artistName is null`() {
    val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t1")))
    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"))) } returns listOf(buildAppTrack("t1", "Song", null))

    val result = runner.run(userId, playlistId, playlist, null, emptyList())

    assertThat(result.violations).containsExactly("Unknown Artist – Song")
  }

  @Test
  fun `canFix returns true`() {
    assertThat(runner.canFix()).isTrue()
  }

  @Test
  fun `fix returns success and makes no Spotify call when no duplicates`() {
    val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t2")))

    val result = runner.fix(userId, accessToken, playlistId, playlist, null, emptyList())

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { spotifyPlaylist.removePlaylistTracks(any(), any(), any(), any()) }
    verify(exactly = 0) { spotifyPlaylist.addPlaylistTracks(any(), any(), any(), any()) }
  }

  @Test
  fun `fix removes all occurrences and re-adds once when track appears twice`() {
    val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t2"), buildTrack("t1")))
    every { spotifyPlaylist.removePlaylistTracks(userId, accessToken, playlistId, listOf("t1")) } returns Unit.right()
    every { spotifyPlaylist.addPlaylistTracks(userId, accessToken, playlistId, listOf("t1")) } returns Unit.right()

    val result = runner.fix(userId, accessToken, playlistId, playlist, null, emptyList())

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { spotifyPlaylist.removePlaylistTracks(userId, accessToken, playlistId, listOf("t1")) }
    verify(exactly = 1) { spotifyPlaylist.addPlaylistTracks(userId, accessToken, playlistId, listOf("t1")) }
  }

  @Test
  fun `fix removes all occurrences and re-adds once when track appears three times`() {
    val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t1"), buildTrack("t1")))
    every { spotifyPlaylist.removePlaylistTracks(userId, accessToken, playlistId, listOf("t1")) } returns Unit.right()
    every { spotifyPlaylist.addPlaylistTracks(userId, accessToken, playlistId, listOf("t1")) } returns Unit.right()

    val result = runner.fix(userId, accessToken, playlistId, playlist, null, emptyList())

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { spotifyPlaylist.removePlaylistTracks(userId, accessToken, playlistId, listOf("t1")) }
    verify(exactly = 1) { spotifyPlaylist.addPlaylistTracks(userId, accessToken, playlistId, listOf("t1")) }
  }

  @Test
  fun `fix handles multiple different duplicate tracks`() {
    val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t2"), buildTrack("t1"), buildTrack("t2")))
    every {
      spotifyPlaylist.removePlaylistTracks(userId, accessToken, playlistId, listOf("t1", "t2"))
    } returns Unit.right()
    every {
      spotifyPlaylist.addPlaylistTracks(userId, accessToken, playlistId, listOf("t1", "t2"))
    } returns Unit.right()

    val result = runner.fix(userId, accessToken, playlistId, playlist, null, emptyList())

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { spotifyPlaylist.removePlaylistTracks(userId, accessToken, playlistId, listOf("t1", "t2")) }
    verify(exactly = 1) { spotifyPlaylist.addPlaylistTracks(userId, accessToken, playlistId, listOf("t1", "t2")) }
  }

  @Test
  fun `fix propagates error from removePlaylistTracks`() {
    val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t1")))
    every { spotifyPlaylist.removePlaylistTracks(any(), any(), any(), any()) } returns PlaylistFixError.FIX_FAILED.left()

    val result = runner.fix(userId, accessToken, playlistId, playlist, null, emptyList())

    assertThat(result.isLeft()).isTrue()
    assertThat((result as Either.Left).value).isEqualTo(PlaylistFixError.FIX_FAILED)
    verify(exactly = 0) { spotifyPlaylist.addPlaylistTracks(any(), any(), any(), any()) }
  }

  @Test
  fun `fix propagates error from addPlaylistTracks`() {
    val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t1")))
    every { spotifyPlaylist.removePlaylistTracks(any(), any(), any(), any()) } returns Unit.right()
    every { spotifyPlaylist.addPlaylistTracks(any(), any(), any(), any()) } returns PlaylistFixError.FIX_FAILED.left()

    val result = runner.fix(userId, accessToken, playlistId, playlist, null, emptyList())

    assertThat(result.isLeft()).isTrue()
    assertThat((result as Either.Left).value).isEqualTo(PlaylistFixError.FIX_FAILED)
  }
}
*/
