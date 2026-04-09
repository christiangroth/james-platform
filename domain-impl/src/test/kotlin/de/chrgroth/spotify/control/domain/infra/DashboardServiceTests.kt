package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.playback.AppPlaybackItem
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.CatalogStats
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogBrowserPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DashboardServiceTests {

  private val appPlaybackRepository: AppPlaybackRepositoryPort = mockk()
  private val appTrackRepository: AppTrackRepositoryPort = mockk()
  private val appArtistRepository: AppArtistRepositoryPort = mockk()
  private val appAlbumRepository: AppAlbumRepositoryPort = mockk()
  private val catalogBrowser: CatalogBrowserPort = mockk()
  private val playlistRepository: PlaylistRepositoryPort = mockk()
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort = mockk()

  private val adapter = DashboardService(
    appPlaybackRepository, appTrackRepository, appArtistRepository, appAlbumRepository,
    catalogBrowser, playlistRepository, playlistCheckRepository,
    recentlyPlayedLimit = 5,
    topEntriesLimit = 3,
  )

  private val userId = UserId("user-1")
  private val syncTimestamp = Instant.fromEpochSeconds(0)

  private val artist1 = AppArtist(
    id = ArtistId("artist-1"), artistName = "Artist One",
    lastSync = syncTimestamp,
  )

  private val track1 = AppTrack(
    id = TrackId("track-1"), title = "Track One",
    artistId = ArtistId("artist-1"), durationMs = 300_000L,
    albumId = AlbumId("album-1"), lastSync = syncTimestamp,
  )

  private fun setupCommonMocks() {
    every { appPlaybackRepository.countAll(userId) } returns 10L
    every { appPlaybackRepository.countSince(userId, any()) } returns 5L
    every { appPlaybackRepository.countPerDaySince(userId, any()) } returns emptyList()
    every { appPlaybackRepository.findRecentlyPlayed(userId, any()) } returns emptyList()
    every { appPlaybackRepository.sumSecondsPlayedByTrackIdSince(userId, any()) } returns emptyMap()
    every { playlistRepository.findByUserId(userId) } returns emptyList()
    every { playlistCheckRepository.countAll() } returns 0L
    every { playlistCheckRepository.countSucceeded() } returns 0L
    every { catalogBrowser.getCatalogStats() } returns CatalogStats(0, 0, 0)
    every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
    every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()
    every { appArtistRepository.findByArtistIds(any()) } returns emptyList()
  }

  @Test
  fun `listening stats exclude items without tracked duration`() {
    setupCommonMocks()

    // MongoDB aggregation filters out secondsPlayed = 0, so only track-1 with 180s is returned
    every { appPlaybackRepository.sumSecondsPlayedByTrackIdSince(userId, any()) } returns mapOf("track-1" to 180L)
    every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"))) } returns listOf(track1)
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns listOf(artist1)
    every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()

    val stats = adapter.getStats(userId)

    // Only the item with duration (180 seconds = 3 minutes) should count
    assertThat(stats.listeningStats.listenedMinutesLast30Days).isEqualTo(3L)
  }

  @Test
  fun `listening stats are zero when all playback items have zero secondsPlayed`() {
    setupCommonMocks()

    // MongoDB aggregation filters out all zero-secondsPlayed items, returning empty map
    every { appPlaybackRepository.sumSecondsPlayedByTrackIdSince(userId, any()) } returns emptyMap()

    val stats = adapter.getStats(userId)

    assertThat(stats.listeningStats.listenedMinutesLast30Days).isEqualTo(0L)
    assertThat(stats.listeningStats.topTracksLast30Days).isEmpty()
    assertThat(stats.listeningStats.topArtistsLast30Days).isEmpty()
  }

  @Test
  fun `listening stats aggregate play durations across multiple tracks`() {
    setupCommonMocks()

    val track2 = AppTrack(
      id = TrackId("track-2"), title = "Track Two",
      artistId = ArtistId("artist-1"), durationMs = 240_000L,
      lastSync = syncTimestamp,
    )
    // MongoDB aggregation groups by trackId and sums seconds; zero-second item for track-1 is excluded
    every { appPlaybackRepository.sumSecondsPlayedByTrackIdSince(userId, any()) } returns mapOf("track-1" to 120L, "track-2" to 180L)
    every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"), TrackId("track-2"))) } returns listOf(track1, track2)
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns listOf(artist1)
    every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()

    val stats = adapter.getStats(userId)

    // track-1: 120s, track-2: 180s → total 300s = 5 minutes; item without duration (0s) ignored
    assertThat(stats.listeningStats.listenedMinutesLast30Days).isEqualTo(5L)
    assertThat(stats.listeningStats.topTracksLast30Days).hasSize(2)
  }

  @Test
  fun `recently played tracks include duration from secondsPlayed`() {
    every { appPlaybackRepository.countAll(userId) } returns 1L
    every { appPlaybackRepository.countSince(userId, any()) } returns 1L
    every { appPlaybackRepository.countPerDaySince(userId, any()) } returns emptyList()
    every { appPlaybackRepository.sumSecondsPlayedByTrackIdSince(userId, any()) } returns emptyMap()
    every { playlistRepository.findByUserId(userId) } returns emptyList()
    every { playlistCheckRepository.countAll() } returns 0L
    every { playlistCheckRepository.countSucceeded() } returns 0L
    every { catalogBrowser.getCatalogStats() } returns CatalogStats(0, 0, 0)
    every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()
    every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
    every { appArtistRepository.findByArtistIds(any()) } returns emptyList()

    val playbackItem = AppPlaybackItem(
      userId = userId,
      playedAt = Instant.fromEpochSeconds(1000),
      trackId = "track-1",
      secondsPlayed = 210L,
    )
    every { appPlaybackRepository.findRecentlyPlayed(userId, any()) } returns listOf(playbackItem)
    every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"))) } returns listOf(track1)
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns listOf(artist1)

    val stats = adapter.getStats(userId)

    assertThat(stats.recentlyPlayedTracks).hasSize(1)
    assertThat(stats.recentlyPlayedTracks[0].durationSeconds).isEqualTo(210L)
  }

  @Test
  fun `getPlaybackStats only queries playback repository`() {
    every { appPlaybackRepository.countAll(userId) } returns 42L
    every { appPlaybackRepository.countSince(userId, any()) } returns 7L
    every { appPlaybackRepository.countPerDaySince(userId, any()) } returns emptyList()

    val stats = adapter.getPlaybackStats(userId)

    assertThat(stats.totalPlaybackEvents).isEqualTo(42L)
    assertThat(stats.playbackEventsLast30Days).isEqualTo(7L)
    assertThat(stats.playbackEventsPerDay).hasSize(30)
    verify(exactly = 0) { playlistRepository.findByUserId(any()) }
    verify(exactly = 0) { playlistCheckRepository.countAll() }
    verify(exactly = 0) { catalogBrowser.getCatalogStats() }
    verify(exactly = 0) { appPlaybackRepository.findRecentlyPlayed(any(), any()) }
    verify(exactly = 0) { appPlaybackRepository.sumSecondsPlayedByTrackIdSince(any(), any()) }
  }

  @Test
  fun `getPlaylistMetadata only queries playlist repository`() {
    every { playlistRepository.findByUserId(userId) } returns emptyList()

    val stats = adapter.getPlaylistMetadata(userId)

    assertThat(stats.syncedPlaylists).isEqualTo(0L)
    assertThat(stats.totalPlaylists).isEqualTo(0L)
    verify(exactly = 0) { appPlaybackRepository.countAll(any()) }
    verify(exactly = 0) { playlistCheckRepository.countAll() }
    verify(exactly = 0) { catalogBrowser.getCatalogStats() }
  }

  @Test
  fun `getRecentlyPlayed only queries playback and catalog repositories for track data`() {
    every { appPlaybackRepository.findRecentlyPlayed(userId, any()) } returns emptyList()
    every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
    every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()
    every { appArtistRepository.findByArtistIds(any()) } returns emptyList()

    val stats = adapter.getRecentlyPlayed(userId)

    assertThat(stats.recentlyPlayedTracks).isEmpty()
    verify(exactly = 0) { appPlaybackRepository.countAll(any()) }
    verify(exactly = 0) { appPlaybackRepository.countSince(any(), any()) }
    verify(exactly = 0) { playlistRepository.findByUserId(any()) }
    verify(exactly = 0) { playlistCheckRepository.countAll() }
    verify(exactly = 0) { catalogBrowser.getCatalogStats() }
  }

  @Test
  fun `getListeningStats only queries playback and catalog repositories for stats`() {
    every { appPlaybackRepository.sumSecondsPlayedByTrackIdSince(userId, any()) } returns emptyMap()
    every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
    every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()
    every { appArtistRepository.findByArtistIds(any()) } returns emptyList()

    val stats = adapter.getListeningStats(userId)

    assertThat(stats.listeningStats.listenedMinutesLast30Days).isEqualTo(0L)
    verify(exactly = 0) { appPlaybackRepository.countAll(any()) }
    verify(exactly = 0) { appPlaybackRepository.countSince(any(), any()) }
    verify(exactly = 0) { playlistRepository.findByUserId(any()) }
    verify(exactly = 0) { playlistCheckRepository.countAll() }
    verify(exactly = 0) { catalogBrowser.getCatalogStats() }
  }

  @Test
  fun `getPlaylistCheckStats only queries playlist check repository`() {
    every { playlistCheckRepository.countAll() } returns 3L
    every { playlistCheckRepository.countSucceeded() } returns 3L

    val stats = adapter.getPlaylistCheckStats()

    assertThat(stats.playlistCheckStats.totalChecks).isEqualTo(3L)
    assertThat(stats.playlistCheckStats.succeededChecks).isEqualTo(3L)
    assertThat(stats.playlistCheckStats.allSucceeded).isTrue()
    verify(exactly = 0) { appPlaybackRepository.countAll(any()) }
    verify(exactly = 0) { playlistRepository.findByUserId(any()) }
    verify(exactly = 0) { catalogBrowser.getCatalogStats() }
  }

  @Test
  fun `getCatalogStats only queries catalog browser`() {
    every { catalogBrowser.getCatalogStats() } returns CatalogStats(artistCount = 10L, albumCount = 20L, trackCount = 30L)

    val stats = adapter.getCatalogStats()

    assertThat(stats.catalogStats.artistCount).isEqualTo(10L)
    assertThat(stats.catalogStats.albumCount).isEqualTo(20L)
    assertThat(stats.catalogStats.trackCount).isEqualTo(30L)
    verify(exactly = 0) { appPlaybackRepository.countAll(any()) }
    verify(exactly = 0) { playlistRepository.findByUserId(any()) }
    verify(exactly = 0) { playlistCheckRepository.countAll() }
  }
}
