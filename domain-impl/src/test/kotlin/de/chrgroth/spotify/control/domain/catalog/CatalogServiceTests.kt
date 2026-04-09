package de.chrgroth.spotify.control.domain.catalog

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.SyncError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AlbumSyncResult
import de.chrgroth.spotify.control.domain.model.catalog.AppAlbum
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.model.user.User
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.catalog.SpotifyCatalogPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CatalogServiceTests {

  private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
  private val spotifyCatalog: SpotifyCatalogPort = mockk()
  private val appArtistRepository: AppArtistRepositoryPort = mockk()
  private val appTrackRepository: AppTrackRepositoryPort = mockk()
  private val appAlbumRepository: AppAlbumRepositoryPort = mockk()
  private val userRepository: UserRepositoryPort = mockk()
  private val outboxPort: OutboxPort = mockk()
  private val playlistRepository: PlaylistRepositoryPort = mockk()
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort = mockk()
  private val dashboardRefresh: DashboardRefreshPort = mockk(relaxed = true)
  private val syncController: SyncController = mockk(relaxed = true)

  private val adapter = CatalogService(
    spotifyAccessToken, spotifyCatalog,
    appArtistRepository, appTrackRepository, appAlbumRepository,
    userRepository, outboxPort,
    playlistRepository, playlistCheckRepository,
    dashboardRefresh, syncController,
  )

  private val syncTimestamp = Instant.fromEpochSeconds(1)
  private val artist1 = AppArtist(
    id = ArtistId("artist-1"), artistName = "Artist One",
    lastSync = syncTimestamp,
  )
  private val artist2 = AppArtist(
    id = ArtistId("artist-2"), artistName = "Artist Two",
    lastSync = syncTimestamp,
  )
  private val track1 = AppTrack(id = TrackId("track-1"), title = "Track One", artistId = ArtistId("artist-1"), lastSync = syncTimestamp)
  private val track2 = AppTrack(id = TrackId("track-2"), title = "Track Two", artistId = ArtistId("artist-2"), lastSync = syncTimestamp)

  private val userId = UserId("user-1")
  private val accessToken = AccessToken("token")
  private val album1 = AppAlbum(id = AlbumId("album-1"), title = "Album One", artistId = ArtistId("artist-1"), artistName = "Artist One", lastSync = syncTimestamp)
  private val trackWithAlbum1 = AppTrack(
    id = TrackId("track-1"), title = "Track One",
    albumId = AlbumId("album-1"), artistId = ArtistId("artist-1"), lastSync = syncTimestamp,
  )
  private val trackWithAlbum2 = AppTrack(
    id = TrackId("track-2"), title = "Track Two",
    albumId = AlbumId("album-1"), artistId = ArtistId("artist-1"), lastSync = syncTimestamp,
  )
  private val trackWithAlbum3 = AppTrack(
    id = TrackId("track-3"), title = "Track Three",
    albumId = AlbumId("album-2"), artistId = ArtistId("artist-2"), lastSync = syncTimestamp,
  )
  private val albumSyncResult = AlbumSyncResult(album = album1, tracks = listOf(trackWithAlbum1, trackWithAlbum2))

  private fun buildUser(id: String = "user-1") = User(
    spotifyUserId = UserId(id),
    displayName = "User $id",
    encryptedAccessToken = "enc-access",
    encryptedRefreshToken = "enc-refresh",
    tokenExpiresAt = Instant.DISTANT_FUTURE,
    lastLoginAt = Instant.fromEpochSeconds(0),
  )

  // --- resyncArtist tests ---

  @Test
  fun `resyncArtist returns error when artist not found`() {
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns emptyList()

    val result = adapter.resyncArtist("artist-1")

    assertThat(result.isLeft()).isTrue()
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `resyncArtist enqueues SyncArtistAlbums for existing artist`() {
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns listOf(artist1)
    every { userRepository.findAll() } returns listOf(buildUser())
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.resyncArtist("artist-1")

    assertThat(result.isRight()).isTrue()
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums("artist-1", userId)) }
    verify(exactly = 0) { outboxPort.enqueue(match { it is DomainOutboxEvent.SyncAlbumDetails }) }
  }

  @Test
  fun `resyncArtist does nothing when no users available`() {
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns listOf(artist1)
    every { userRepository.findAll() } returns emptyList()

    val result = adapter.resyncArtist("artist-1")

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  // --- resyncCatalog tests ---

  @Test
  fun `resyncCatalog does nothing when catalog is empty`() {
    every { appArtistRepository.findAll() } returns emptyList()
    every { userRepository.findAll() } returns listOf(buildUser())

    val result = adapter.resyncCatalog()

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `resyncCatalog enqueues SyncArtistAlbums for all artists`() {
    every { appArtistRepository.findAll() } returns listOf(artist1, artist2)
    every { userRepository.findAll() } returns listOf(buildUser())
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.resyncCatalog()

    assertThat(result.isRight()).isTrue()
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums("artist-1", userId)) }
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums("artist-2", userId)) }
  }

  @Test
  fun `resyncCatalog does not enqueue events when no users available`() {
    every { appArtistRepository.findAll() } returns listOf(artist1)
    every { userRepository.findAll() } returns emptyList()

    val result = adapter.resyncCatalog()

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  // --- handle(ResyncCatalog) tests ---

  @Test
  fun `handle ResyncCatalog returns success`() {
    every { appArtistRepository.findAll() } returns listOf(artist1)
    every { userRepository.findAll() } returns listOf(buildUser())
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.handle(DomainOutboxEvent.ResyncCatalog())

    assertThat(result.isRight()).isTrue()
  }

  @Test
  fun `handle ResyncCatalog returns success when catalog is empty`() {
    every { appArtistRepository.findAll() } returns emptyList()
    every { userRepository.findAll() } returns emptyList()

    val result = adapter.handle(DomainOutboxEvent.ResyncCatalog())

    assertThat(result.isRight()).isTrue()
  }

  // --- SyncAlbumDetails tests ---

  @Test
  fun `handle SyncAlbumDetails returns success when no users available`() {
    every { userRepository.findAll() } returns emptyList()

    val result = adapter.handle(DomainOutboxEvent.SyncAlbumDetails("album-1"))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { spotifyCatalog.getAlbum(any(), any(), any()) }
  }

  @Test
  fun `handle SyncAlbumDetails syncs album and upserts all tracks`() {
    every { userRepository.findAll() } returns listOf(buildUser())
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyCatalog.getAlbum(userId, accessToken, "album-1") } returns albumSyncResult.right()
    every { appTrackRepository.upsertAll(any()) } just runs
    every { appAlbumRepository.upsertAll(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.handle(DomainOutboxEvent.SyncAlbumDetails("album-1"))

    assertThat(result.isRight()).isTrue()
    verify { spotifyCatalog.getAlbum(userId, accessToken, "album-1") }
    verify { appTrackRepository.upsertAll(listOf(trackWithAlbum1, trackWithAlbum2)) }
    verify { appAlbumRepository.upsertAll(listOf(album1)) }
  }

  @Test
  fun `handle SyncAlbumDetails enqueues SyncArtistDetails for artists found in album`() {
    every { userRepository.findAll() } returns listOf(buildUser())
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyCatalog.getAlbum(userId, accessToken, "album-1") } returns albumSyncResult.right()
    every { appTrackRepository.upsertAll(any()) } just runs
    every { appAlbumRepository.upsertAll(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs

    adapter.handle(DomainOutboxEvent.SyncAlbumDetails("album-1"))

    verify { syncController.syncArtists(listOf("artist-1"), userId) }
  }

  @Test
  fun `handle SyncAlbumDetails does not enqueue SyncArtistDetails when artist already exists`() {
    every { userRepository.findAll() } returns listOf(buildUser())
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyCatalog.getAlbum(userId, accessToken, "album-1") } returns albumSyncResult.right()
    every { appTrackRepository.upsertAll(any()) } just runs
    every { appAlbumRepository.upsertAll(any()) } just runs

    adapter.handle(DomainOutboxEvent.SyncAlbumDetails("album-1"))

    verify { syncController.syncArtists(listOf("artist-1"), userId) }
  }

  @Test
  fun `handle SyncAlbumDetails returns failed when album endpoint returns error`() {
    every { userRepository.findAll() } returns listOf(buildUser())
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyCatalog.getAlbum(userId, accessToken, "album-1") } returns SyncError.TRACK_DETAILS_FETCH_FAILED.left()

    val result = adapter.handle(DomainOutboxEvent.SyncAlbumDetails("album-1"))

    assertThat(result.isLeft()).isTrue()
    verify(exactly = 0) { appTrackRepository.upsertAll(any()) }
  }

  @Test
  fun `handle SyncAlbumDetails returns rate limited when endpoint returns rate limit error`() {
    val rateLimitError = SpotifyRateLimitError(30.seconds)
    every { userRepository.findAll() } returns listOf(buildUser())
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyCatalog.getAlbum(userId, accessToken, "album-1") } returns rateLimitError.left()

    val result = adapter.handle(DomainOutboxEvent.SyncAlbumDetails("album-1"))

    assertThat(result.isLeft()).isTrue()
  }

  // --- wipeCatalog tests ---

  @Test
  fun `wipeCatalog deletes all catalog data, deactivates playlists and deletes checks`() {
    every { appArtistRepository.deleteAll() } just runs
    every { appAlbumRepository.deleteAll() } just runs
    every { appTrackRepository.deleteAll() } just runs
    every { playlistRepository.setAllSyncInactive() } just runs
    every { playlistCheckRepository.deleteAll() } just runs

    val result = adapter.wipeCatalog()

    assertThat(result.isRight()).isTrue()
    verify { appArtistRepository.deleteAll() }
    verify { appAlbumRepository.deleteAll() }
    verify { appTrackRepository.deleteAll() }
    verify { playlistRepository.setAllSyncInactive() }
    verify { playlistCheckRepository.deleteAll() }
  }
}
