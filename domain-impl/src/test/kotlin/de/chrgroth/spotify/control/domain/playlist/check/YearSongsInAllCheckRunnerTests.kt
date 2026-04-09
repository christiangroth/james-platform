package de.chrgroth.spotify.control.domain.playlist.check

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
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

class YearSongsInAllCheckRunnerTests {

  private val playlistRepository: PlaylistRepositoryPort = mockk()
  private val appTrackRepository: AppTrackRepositoryPort = mockk()
  private val spotifyPlaylist: SpotifyPlaylistPort = mockk()
  private val runner = YearSongsInAllCheckRunner(playlistRepository, appTrackRepository, spotifyPlaylist)

  private val userId = UserId("user-1")
  private val accessToken = AccessToken("token")
  private val playlistId = "playlist-1"
  private val allPlaylistId = "playlist-all"

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

  private fun buildPlaylist(spotifyPlaylistId: String = playlistId, tracks: List<PlaylistTrack>) = Playlist(
    spotifyPlaylistId = spotifyPlaylistId,
    tracks = tracks,
  )

  private fun buildPlaylistInfo(spotifyPlaylistId: String = playlistId, type: PlaylistType? = null) = PlaylistInfo(
    spotifyPlaylistId = spotifyPlaylistId,
    snapshotId = "snap-1",
    lastSnapshotIdSyncTime = Clock.System.now(),
    name = "Playlist $spotifyPlaylistId",
    syncStatus = PlaylistSyncStatus.ACTIVE,
    type = type,
  )

  @Test
  fun `isApplicable returns true only for YEAR playlists`() {
    assertThat(runner.isApplicable(null)).isFalse()
    assertThat(runner.isApplicable(buildPlaylistInfo(type = PlaylistType.YEAR))).isTrue()
    assertThat(runner.isApplicable(buildPlaylistInfo(type = PlaylistType.ALL))).isFalse()
    assertThat(runner.isApplicable(buildPlaylistInfo(type = PlaylistType.UNKNOWN))).isFalse()
  }

  @Test
  fun `run passes when no all playlist exists`() {
    val playlist = buildPlaylist(tracks = listOf(buildTrack("t1")))
    val currentPlaylistInfo = buildPlaylistInfo(type = PlaylistType.YEAR)
    val playlistInfos = listOf(currentPlaylistInfo)

    val result = runner.run(userId, playlistId, playlist, currentPlaylistInfo, playlistInfos)

    assertThat(result.succeeded).isTrue()
    assertThat(result.violations).isEmpty()
    assertThat(result.checkId).isEqualTo("$playlistId:year-songs-in-all")
  }

  @Test
  fun `run passes when all playlist is not yet synced`() {
    val playlist = buildPlaylist(tracks = listOf(buildTrack("t1")))
    val currentPlaylistInfo = buildPlaylistInfo(type = PlaylistType.YEAR)
    val playlistInfos = listOf(
      currentPlaylistInfo,
      buildPlaylistInfo(spotifyPlaylistId = allPlaylistId, type = PlaylistType.ALL),
    )
    every { playlistRepository.findByUserIdAndPlaylistId(userId, allPlaylistId) } returns null

    val result = runner.run(userId, playlistId, playlist, currentPlaylistInfo, playlistInfos)

    assertThat(result.succeeded).isTrue()
    assertThat(result.violations).isEmpty()
  }

  @Test
  fun `run passes when all tracks are in all playlist`() {
    val playlist = buildPlaylist(tracks = listOf(buildTrack("t1"), buildTrack("t2")))
    val allPlaylist = buildPlaylist(spotifyPlaylistId = allPlaylistId, tracks = listOf(buildTrack("t1"), buildTrack("t2"), buildTrack("t3")))
    val currentPlaylistInfo = buildPlaylistInfo(type = PlaylistType.YEAR)
    val playlistInfos = listOf(
      currentPlaylistInfo,
      buildPlaylistInfo(spotifyPlaylistId = allPlaylistId, type = PlaylistType.ALL),
    )
    every { playlistRepository.findByUserIdAndPlaylistId(userId, allPlaylistId) } returns allPlaylist

    val result = runner.run(userId, playlistId, playlist, currentPlaylistInfo, playlistInfos)

    assertThat(result.succeeded).isTrue()
    assertThat(result.violations).isEmpty()
  }

  @Test
  fun `run reports violations for tracks missing from all playlist`() {
    val playlist = buildPlaylist(
      tracks = listOf(buildTrack("t1"), buildTrack("t2"), buildTrack("t3")),
    )
    val allPlaylist = buildPlaylist(spotifyPlaylistId = allPlaylistId, tracks = listOf(buildTrack("t1")))
    val currentPlaylistInfo = buildPlaylistInfo(type = PlaylistType.YEAR)
    val playlistInfos = listOf(
      currentPlaylistInfo,
      buildPlaylistInfo(spotifyPlaylistId = allPlaylistId, type = PlaylistType.ALL),
    )
    every { playlistRepository.findByUserIdAndPlaylistId(userId, allPlaylistId) } returns allPlaylist
    every { appTrackRepository.findByTrackIds(setOf(TrackId("t2"), TrackId("t3"))) } returns listOf(
      buildAppTrack("t2", "Song B", "Artist B"),
      buildAppTrack("t3", "Song C", "Artist C"),
    )

    val result = runner.run(userId, playlistId, playlist, currentPlaylistInfo, playlistInfos)

    assertThat(result.succeeded).isFalse()
    assertThat(result.violations).containsExactlyInAnyOrder("Artist B – Song B", "Artist C – Song C")
  }

  @Test
  fun `run deduplicates violations for duplicate missing tracks`() {
    val playlist = buildPlaylist(
      tracks = listOf(buildTrack("t1"), buildTrack("t2"), buildTrack("t2")),
    )
    val allPlaylist = buildPlaylist(spotifyPlaylistId = allPlaylistId, tracks = listOf(buildTrack("t1")))
    val currentPlaylistInfo = buildPlaylistInfo(type = PlaylistType.YEAR)
    val playlistInfos = listOf(
      currentPlaylistInfo,
      buildPlaylistInfo(spotifyPlaylistId = allPlaylistId, type = PlaylistType.ALL),
    )
    every { playlistRepository.findByUserIdAndPlaylistId(userId, allPlaylistId) } returns allPlaylist
    every { appTrackRepository.findByTrackIds(setOf(TrackId("t2"))) } returns listOf(
      buildAppTrack("t2", "Song B", "Artist B"),
    )

    val result = runner.run(userId, playlistId, playlist, currentPlaylistInfo, playlistInfos)

    assertThat(result.succeeded).isFalse()
    assertThat(result.violations).containsExactly("Artist B – Song B")
  }

  @Test
  fun `run falls back to unknown artist when artistName is null`() {
    val playlist = buildPlaylist(tracks = listOf(buildTrack("t1"), buildTrack("t2")))
    val allPlaylist = buildPlaylist(spotifyPlaylistId = allPlaylistId, tracks = listOf(buildTrack("t1")))
    val currentPlaylistInfo = buildPlaylistInfo(type = PlaylistType.YEAR)
    val playlistInfos = listOf(
      currentPlaylistInfo,
      buildPlaylistInfo(spotifyPlaylistId = allPlaylistId, type = PlaylistType.ALL),
    )
    every { playlistRepository.findByUserIdAndPlaylistId(userId, allPlaylistId) } returns allPlaylist
    every { appTrackRepository.findByTrackIds(setOf(TrackId("t2"))) } returns listOf(
      buildAppTrack("t2", "Song B", null),
    )

    val result = runner.run(userId, playlistId, playlist, currentPlaylistInfo, playlistInfos)

    assertThat(result.violations).containsExactly("Unknown Artist – Song B")
  }

  @Test
  fun `canFix returns true`() {
    assertThat(runner.canFix()).isTrue()
  }

  @Test
  fun `fix returns FIX_NOT_FOUND when no all playlist info exists`() {
    val playlist = buildPlaylist(tracks = listOf(buildTrack("t1")))
    val currentPlaylistInfo = buildPlaylistInfo(type = PlaylistType.YEAR)
    val playlistInfos = listOf(currentPlaylistInfo)

    val result = runner.fix(userId, accessToken, playlistId, playlist, currentPlaylistInfo, playlistInfos)

    assertThat(result.isLeft()).isTrue()
    assertThat((result as Either.Left).value).isEqualTo(PlaylistFixError.FIX_NOT_FOUND)
    verify(exactly = 0) { spotifyPlaylist.addPlaylistTracks(any(), any(), any(), any()) }
  }

  @Test
  fun `fix returns PLAYLIST_NOT_FOUND when all playlist not yet synced`() {
    val playlist = buildPlaylist(tracks = listOf(buildTrack("t1")))
    val currentPlaylistInfo = buildPlaylistInfo(type = PlaylistType.YEAR)
    val playlistInfos = listOf(
      currentPlaylistInfo,
      buildPlaylistInfo(spotifyPlaylistId = allPlaylistId, type = PlaylistType.ALL),
    )
    every { playlistRepository.findByUserIdAndPlaylistId(userId, allPlaylistId) } returns null

    val result = runner.fix(userId, accessToken, playlistId, playlist, currentPlaylistInfo, playlistInfos)

    assertThat(result.isLeft()).isTrue()
    assertThat((result as Either.Left).value).isEqualTo(PlaylistFixError.PLAYLIST_NOT_FOUND)
    verify(exactly = 0) { spotifyPlaylist.addPlaylistTracks(any(), any(), any(), any()) }
  }

  @Test
  fun `fix returns success and makes no Spotify call when no tracks are missing`() {
    val playlist = buildPlaylist(tracks = listOf(buildTrack("t1"), buildTrack("t2")))
    val allPlaylist = buildPlaylist(spotifyPlaylistId = allPlaylistId, tracks = listOf(buildTrack("t1"), buildTrack("t2")))
    val currentPlaylistInfo = buildPlaylistInfo(type = PlaylistType.YEAR)
    val playlistInfos = listOf(
      currentPlaylistInfo,
      buildPlaylistInfo(spotifyPlaylistId = allPlaylistId, type = PlaylistType.ALL),
    )
    every { playlistRepository.findByUserIdAndPlaylistId(userId, allPlaylistId) } returns allPlaylist

    val result = runner.fix(userId, accessToken, playlistId, playlist, currentPlaylistInfo, playlistInfos)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { spotifyPlaylist.addPlaylistTracks(any(), any(), any(), any()) }
  }

  @Test
  fun `fix adds missing tracks to all playlist`() {
    val playlist = buildPlaylist(tracks = listOf(buildTrack("t1"), buildTrack("t2"), buildTrack("t3")))
    val allPlaylist = buildPlaylist(spotifyPlaylistId = allPlaylistId, tracks = listOf(buildTrack("t1")))
    val currentPlaylistInfo = buildPlaylistInfo(type = PlaylistType.YEAR)
    val playlistInfos = listOf(
      currentPlaylistInfo,
      buildPlaylistInfo(spotifyPlaylistId = allPlaylistId, type = PlaylistType.ALL),
    )
    every { playlistRepository.findByUserIdAndPlaylistId(userId, allPlaylistId) } returns allPlaylist
    every { spotifyPlaylist.addPlaylistTracks(userId, accessToken, allPlaylistId, any()) } returns Unit.right()

    val result = runner.fix(userId, accessToken, playlistId, playlist, currentPlaylistInfo, playlistInfos)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) {
      spotifyPlaylist.addPlaylistTracks(userId, accessToken, allPlaylistId, match { it.containsAll(listOf("t2", "t3")) && it.size == 2 })
    }
  }

  @Test
  fun `fix deduplicates missing tracks before adding`() {
    val playlist = buildPlaylist(tracks = listOf(buildTrack("t1"), buildTrack("t2"), buildTrack("t2")))
    val allPlaylist = buildPlaylist(spotifyPlaylistId = allPlaylistId, tracks = listOf(buildTrack("t1")))
    val currentPlaylistInfo = buildPlaylistInfo(type = PlaylistType.YEAR)
    val playlistInfos = listOf(
      currentPlaylistInfo,
      buildPlaylistInfo(spotifyPlaylistId = allPlaylistId, type = PlaylistType.ALL),
    )
    every { playlistRepository.findByUserIdAndPlaylistId(userId, allPlaylistId) } returns allPlaylist
    every { spotifyPlaylist.addPlaylistTracks(userId, accessToken, allPlaylistId, listOf("t2")) } returns Unit.right()

    val result = runner.fix(userId, accessToken, playlistId, playlist, currentPlaylistInfo, playlistInfos)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { spotifyPlaylist.addPlaylistTracks(userId, accessToken, allPlaylistId, listOf("t2")) }
  }

  @Test
  fun `fix propagates error from Spotify`() {
    val playlist = buildPlaylist(tracks = listOf(buildTrack("t1"), buildTrack("t2")))
    val allPlaylist = buildPlaylist(spotifyPlaylistId = allPlaylistId, tracks = listOf(buildTrack("t1")))
    val currentPlaylistInfo = buildPlaylistInfo(type = PlaylistType.YEAR)
    val playlistInfos = listOf(
      currentPlaylistInfo,
      buildPlaylistInfo(spotifyPlaylistId = allPlaylistId, type = PlaylistType.ALL),
    )
    every { playlistRepository.findByUserIdAndPlaylistId(userId, allPlaylistId) } returns allPlaylist
    every { spotifyPlaylist.addPlaylistTracks(any(), any(), any(), any()) } returns PlaylistFixError.FIX_FAILED.left()

    val result = runner.fix(userId, accessToken, playlistId, playlist, currentPlaylistInfo, playlistInfos)

    assertThat(result.isLeft()).isTrue()
    assertThat((result as Either.Left).value).isEqualTo(PlaylistFixError.FIX_FAILED)
  }
}
