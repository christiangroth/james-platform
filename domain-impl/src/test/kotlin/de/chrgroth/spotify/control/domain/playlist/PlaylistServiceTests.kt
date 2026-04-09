package de.chrgroth.spotify.control.domain.playlist

/*
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTracksPage
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.model.playlist.SpotifyPlaylistItem
import de.chrgroth.spotify.control.domain.model.user.User
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import de.chrgroth.spotify.control.domain.catalog.SyncController
import de.chrgroth.spotify.control.domain.catalog.CatalogSyncRequest
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@Suppress("LargeClass")
class PlaylistServiceTests {

  private val userRepository: UserRepositoryPort = mockk()
  private val playlistRepository: PlaylistRepositoryPort = mockk()
  private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
  private val spotifyPlaylist: SpotifyPlaylistPort = mockk()
  private val outboxPort: OutboxPort = mockk()
  private val dashboardRefresh: DashboardRefreshPort = mockk()
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort = mockk()
  private val syncController: SyncController = mockk(relaxed = true)

  private val adapter = PlaylistService(
    userRepository, playlistRepository,
    spotifyAccessToken, spotifyPlaylist,
    outboxPort, dashboardRefresh,
    playlistCheckRepository,
    syncController,
  )

  private val userId = UserId("user-1")
  private val accessToken = AccessToken("access-token")
  private val now = Clock.System.now()

  private fun buildUser(id: String = "user-1") = User(
    spotifyUserId = UserId(id),
    displayName = "User $id",
    encryptedAccessToken = "enc-access",
    encryptedRefreshToken = "enc-refresh",
    tokenExpiresAt = now + 1.hours,
    lastLoginAt = now,
  )

  private fun buildPlaylistInfo(
    id: String,
    snapshotId: String = "snap-1",
    syncStatus: PlaylistSyncStatus = PlaylistSyncStatus.ACTIVE,
    name: String = "Playlist $id",
  ) = PlaylistInfo(
    spotifyPlaylistId = id,
    snapshotId = snapshotId,
    lastSnapshotIdSyncTime = now - 1.hours,
    name = name,
    syncStatus = syncStatus,
  )

  private fun buildSpotifyItem(id: String, snapshotId: String = "snap-1", ownerId: String = "user-1") = SpotifyPlaylistItem(
    id = id,
    name = "Playlist $id",
    snapshotId = snapshotId,
    ownerId = ownerId,
  )

  // --- enqueueUpdates tests ---

  @Test
  fun `enqueueUpdates does nothing when no users exist`() {
    every { userRepository.findAll() } returns emptyList()

    adapter.enqueueUpdates()

    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `enqueueUpdates enqueues one task per user`() {
    every { userRepository.findAll() } returns listOf(buildUser("user-1"), buildUser("user-2"))
    every { outboxPort.enqueue(any()) } just runs

    adapter.enqueueUpdates()

    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistInfo(UserId("user-1"))) }
    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistInfo(UserId("user-2"))) }
  }

  // --- syncPlaylists tests ---

  @Test
  fun `syncPlaylists skips when user not found`() {
    every { userRepository.findById(userId) } returns null

    val result = adapter.syncPlaylists(userId)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { spotifyPlaylist.getPlaylists(any(), any()) }
  }

  @Test
  fun `syncPlaylists filters out playlists not owned by user`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(
      buildSpotifyItem("p1", ownerId = "user-1"),
      buildSpotifyItem("p2", ownerId = "other-user"),
    ).right()
    every { playlistRepository.findByUserId(userId) } returns emptyList()
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistMetadata(userId) } just runs

    val result = adapter.syncPlaylists(userId)

    assertThat(result.isRight()).isTrue()
    val savedSlot = slot<List<PlaylistInfo>>()
    verify { playlistRepository.replaceAll(userId, capture(savedSlot)) }
    assertThat(savedSlot.captured).hasSize(1)
    assertThat(savedSlot.captured[0].spotifyPlaylistId).isEqualTo("p1")
  }

  @Test
  fun `syncPlaylists persists new playlists with PASSIVE status`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1")).right()
    every { playlistRepository.findByUserId(userId) } returns emptyList()
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistMetadata(userId) } just runs

    val result = adapter.syncPlaylists(userId)

    assertThat(result.isRight()).isTrue()
    val savedSlot = slot<List<PlaylistInfo>>()
    verify { playlistRepository.replaceAll(userId, capture(savedSlot)) }
    assertThat(savedSlot.captured).hasSize(1)
    assertThat(savedSlot.captured[0].spotifyPlaylistId).isEqualTo("p1")
    assertThat(savedSlot.captured[0].syncStatus).isEqualTo(PlaylistSyncStatus.PASSIVE)
  }

  @Test
  fun `syncPlaylists preserves existing syncStatus`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1")).right()
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE))
    every { playlistRepository.replaceAll(any(), any()) } just runs

    adapter.syncPlaylists(userId)

    val savedSlot = slot<List<PlaylistInfo>>()
    verify { playlistRepository.replaceAll(userId, capture(savedSlot)) }
    assertThat(savedSlot.captured[0].syncStatus).isEqualTo(PlaylistSyncStatus.PASSIVE)
  }

  @Test
  fun `syncPlaylists uses latest playlist state after Spotify API call to preserve syncStatus changes made in the meantime`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1")).right()
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.ACTIVE))
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { playlistRepository.findByUserIdAndPlaylistId(userId, "p1") } returns mockk()

    adapter.syncPlaylists(userId)

    val savedSlot = slot<List<PlaylistInfo>>()
    verify { playlistRepository.replaceAll(userId, capture(savedSlot)) }
    assertThat(savedSlot.captured[0].syncStatus).isEqualTo(PlaylistSyncStatus.ACTIVE)
  }

  @Test
  fun `syncPlaylists preserves lastSnapshotIdSyncTime when snapshotId is unchanged`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1", snapshotId = "snap-1")).right()
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1", snapshotId = "snap-1"))
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { playlistRepository.findByUserIdAndPlaylistId(userId, "p1") } returns mockk()

    adapter.syncPlaylists(userId)

    val savedSlot = slot<List<PlaylistInfo>>()
    verify { playlistRepository.replaceAll(userId, capture(savedSlot)) }
    assertThat(savedSlot.captured[0].lastSnapshotIdSyncTime).isEqualTo(now - 1.hours)
  }

  @Test
  fun `syncPlaylists updates lastSnapshotIdSyncTime when snapshotId changes`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1", snapshotId = "snap-2")).right()
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1", snapshotId = "snap-1"))
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { outboxPort.enqueue(any()) } just runs

    adapter.syncPlaylists(userId)

    val savedSlot = slot<List<PlaylistInfo>>()
    verify { playlistRepository.replaceAll(userId, capture(savedSlot)) }
    assertThat(savedSlot.captured[0].lastSnapshotIdSyncTime).isGreaterThan(now - 1.hours)
  }

  @Test
  fun `syncPlaylists notifies dashboard when playlist count increases`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1"), buildSpotifyItem("p2")).right()
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1"))
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { playlistRepository.findByUserIdAndPlaylistId(userId, "p1") } returns mockk()
    every { outboxPort.enqueue(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistMetadata(userId) } just runs

    val result = adapter.syncPlaylists(userId)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { dashboardRefresh.notifyUserPlaylistMetadata(userId) }
  }

  @Test
  fun `syncPlaylists notifies dashboard when playlist count decreases`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1")).right()
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1"), buildPlaylistInfo("p2"))
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { playlistRepository.findByUserIdAndPlaylistId(userId, "p1") } returns mockk()
    every { dashboardRefresh.notifyUserPlaylistMetadata(userId) } just runs

    val result = adapter.syncPlaylists(userId)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { dashboardRefresh.notifyUserPlaylistMetadata(userId) }
  }

  @Test
  fun `syncPlaylists does not notify dashboard when playlist count is unchanged`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1")).right()
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1"))
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { playlistRepository.findByUserIdAndPlaylistId(userId, "p1") } returns mockk()

    val result = adapter.syncPlaylists(userId)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { dashboardRefresh.notifyUserPlaylistMetadata(any()) }
  }

  @Test
  fun `syncPlaylists returns Left when spotify fetch fails`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns PlaylistSyncError.PLAYLIST_FETCH_FAILED.left()

    val result = adapter.syncPlaylists(userId)

    assertThat(result.isLeft()).isTrue()
    verify(exactly = 0) { playlistRepository.replaceAll(any(), any()) }
  }

  @Test
  fun `syncPlaylists returns Left with SpotifyRateLimitError when rate limited`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns SpotifyRateLimitError(30.seconds).left()

    val result = adapter.syncPlaylists(userId)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isInstanceOf(SpotifyRateLimitError::class.java)
  }

  // --- updateSyncStatus tests ---

  @Test
  fun `updateSyncStatus returns Left when user not found`() {
    every { userRepository.findById(userId) } returns null

    val result = adapter.updateSyncStatus(userId, "p1", PlaylistSyncStatus.PASSIVE)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(PlaylistSyncError.PLAYLIST_NOT_FOUND)
  }

  @Test
  fun `updateSyncStatus returns Left when playlist not found`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1"))

    val result = adapter.updateSyncStatus(userId, "p-unknown", PlaylistSyncStatus.PASSIVE)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(PlaylistSyncError.PLAYLIST_NOT_FOUND)
  }

  @Test
  fun `updateSyncStatus updates only the target playlist`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { playlistRepository.findByUserId(userId) } returns listOf(
      buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.ACTIVE),
      buildPlaylistInfo("p2", syncStatus = PlaylistSyncStatus.ACTIVE),
    )
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { playlistRepository.findByUserIdAndPlaylistId(userId, "p1") } returns mockk()
    every { dashboardRefresh.notifyUserPlaylistMetadata(userId) } just runs
    every { playlistCheckRepository.deleteByPlaylistId("p1") } just runs

    val result = adapter.updateSyncStatus(userId, "p1", PlaylistSyncStatus.PASSIVE)

    assertThat(result.isRight()).isTrue()
    val savedSlot = slot<List<PlaylistInfo>>()
    verify { playlistRepository.replaceAll(userId, capture(savedSlot)) }
    val updated = savedSlot.captured.associateBy { it.spotifyPlaylistId }
    assertThat(updated["p1"]!!.syncStatus).isEqualTo(PlaylistSyncStatus.PASSIVE)
    assertThat(updated["p2"]!!.syncStatus).isEqualTo(PlaylistSyncStatus.ACTIVE)
  }

  @Test
  fun `syncPlaylists enqueues SyncPlaylistData for active playlist with changed snapshotId`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1", snapshotId = "snap-2")).right()
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1", snapshotId = "snap-1"))
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { outboxPort.enqueue(any()) } just runs

    adapter.syncPlaylists(userId)

    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(userId, "p1")) }
  }

  @Test
  fun `syncPlaylists enqueues SyncPlaylistData for active playlist with no existing playlist data`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1")).right()
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1"))
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { playlistRepository.findByUserIdAndPlaylistId(userId, "p1") } returns null
    every { outboxPort.enqueue(any()) } just runs

    adapter.syncPlaylists(userId)

    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(userId, "p1")) }
  }

  @Test
  fun `syncPlaylists does not enqueue SyncPlaylistData for passive playlist`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1")).right()
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE))
    every { playlistRepository.replaceAll(any(), any()) } just runs

    adapter.syncPlaylists(userId)

    verify(exactly = 0) { outboxPort.enqueue(any<DomainOutboxEvent.SyncPlaylistData>()) }
  }

  @Test
  fun `syncPlaylists does not enqueue SyncPlaylistData for active playlist with unchanged snapshotId and existing data`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1")).right()
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1"))
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { playlistRepository.findByUserIdAndPlaylistId(userId, "p1") } returns mockk()

    adapter.syncPlaylists(userId)

    verify(exactly = 0) { outboxPort.enqueue(any<DomainOutboxEvent.SyncPlaylistData>()) }
  }

  // --- syncPlaylistData tests ---

  private val singleTrack = PlaylistTrack(
    trackId = TrackId("track-1"),
    artistIds = listOf(ArtistId("artist-1")),
    albumId = AlbumId("album-1"),
  )

  private fun buildPlaylist(id: String) = Playlist(
    spotifyPlaylistId = id,
    tracks = listOf(singleTrack),
  )

  private fun buildTracksPage(tracks: List<PlaylistTrack> = listOf(singleTrack), snapshotId: String = "snap-1", nextUrl: String? = null) =
    de.chrgroth.spotify.control.domain.model.playlist.PlaylistTracksPage(
      snapshotId = snapshotId,
      tracks = tracks,
      nextUrl = nextUrl,
    )

  @Test
  fun `syncPlaylistData skips when user not found`() {
    every { userRepository.findById(userId) } returns null

    val result = adapter.syncPlaylistData(userId, "p1")

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { spotifyPlaylist.getPlaylistTracksPage(any(), any(), any(), any()) }
  }

  @Test
  fun `syncPlaylistData fetches and saves playlist tracks on first page`() {
    val user = buildUser()
    val page = buildTracksPage()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(userId, accessToken, "p1", null) } returns page.right()
    every { playlistRepository.save(userId, buildPlaylist("p1")) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { playlistRepository.updateLastSyncTime(userId, "p1", any()) } just runs

    val result = adapter.syncPlaylistData(userId, "p1")

    assertThat(result.isRight()).isTrue()
    verify { playlistRepository.save(userId, buildPlaylist("p1")) }
  }

  @Test
  fun `syncPlaylistData delegates catalog sync to syncController`() {
    val user = buildUser()
    val page = buildTracksPage()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(userId, accessToken, "p1", null) } returns page.right()
    every { playlistRepository.save(userId, any()) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { playlistRepository.updateLastSyncTime(userId, "p1", any()) } just runs

    adapter.syncPlaylistData(userId, "p1")

    verify {
      syncController.syncForTracks(
        listOf(CatalogSyncRequest("track-1", listOf("artist-1"))),
        userId,
      )
    }
  }

  @Test
  fun `syncPlaylistData updates lastSyncTime only on last page`() {
    val user = buildUser()
    val page = buildTracksPage()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(userId, accessToken, "p1", null) } returns page.right()
    every { playlistRepository.save(userId, any()) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { playlistRepository.updateLastSyncTime(userId, "p1", any()) } just runs

    adapter.syncPlaylistData(userId, "p1")

    verify(exactly = 1) { playlistRepository.updateLastSyncTime(eq(userId), eq("p1"), any()) }
  }

  @Test
  fun `syncPlaylistData does not update lastSyncTime when there is a next page`() {
    val user = buildUser()
    val nextPageUrl = "https://api.spotify.com/v1/playlists/p1/tracks?offset=50&limit=50"
    val page = buildTracksPage(nextUrl = nextPageUrl)
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(userId, accessToken, "p1", null) } returns page.right()
    every { playlistRepository.save(userId, any()) } just runs
    every { outboxPort.enqueue(any()) } just runs

    adapter.syncPlaylistData(userId, "p1")

    verify(exactly = 0) { playlistRepository.updateLastSyncTime(any(), any(), any()) }
  }

  @Test
  fun `syncPlaylistData enqueues next page event when nextUrl is present`() {
    val user = buildUser()
    val nextPageUrl = "https://api.spotify.com/v1/playlists/p1/tracks?offset=50&limit=50"
    val page = buildTracksPage(nextUrl = nextPageUrl)
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(userId, accessToken, "p1", null) } returns page.right()
    every { playlistRepository.save(userId, any()) } just runs
    every { outboxPort.enqueue(any()) } just runs

    adapter.syncPlaylistData(userId, "p1")

    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(userId, "p1", nextPageUrl, "snap-1")) }
    verify(exactly = 0) { outboxPort.enqueue(any<DomainOutboxEvent.RunPlaylistChecks>()) }
  }

  @Test
  fun `syncPlaylistData enqueues RunPlaylistChecks only on last page`() {
    val user = buildUser()
    val page = buildTracksPage()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(userId, accessToken, "p1", null) } returns page.right()
    every { playlistRepository.save(userId, any()) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { playlistRepository.updateLastSyncTime(userId, "p1", any()) } just runs

    adapter.syncPlaylistData(userId, "p1")

    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks(userId, "p1")) }
    verify(exactly = 0) { outboxPort.enqueue(any<DomainOutboxEvent.SyncPlaylistData>()) }
  }

  @Test
  fun `syncPlaylistData appends tracks on subsequent pages`() {
    val user = buildUser()
    val nextPageUrl = "https://api.spotify.com/v1/playlists/p1/tracks?offset=50&limit=50"
    val page = buildTracksPage()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(userId, accessToken, "p1", nextPageUrl) } returns page.right()
    every { playlistRepository.appendTracks(userId, "p1", page.tracks) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { playlistRepository.updateLastSyncTime(userId, "p1", any()) } just runs

    val result = adapter.syncPlaylistData(userId, "p1", nextPageUrl)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { playlistRepository.appendTracks(userId, "p1", page.tracks) }
    verify(exactly = 0) { playlistRepository.save(any(), any()) }
  }

  @Test
  fun `syncPlaylistData restarts from first page when snapshotId changes mid-paging`() {
    val user = buildUser()
    val nextPageUrl = "https://api.spotify.com/v1/playlists/p1/tracks?offset=50&limit=50"
    val page = buildTracksPage(snapshotId = "snap-2")  // snapshot changed
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(userId, accessToken, "p1", nextPageUrl) } returns page.right()
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.syncPlaylistData(userId, "p1", nextPageUrl, "snap-1")

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(userId, "p1")) }
    verify(exactly = 0) { playlistRepository.appendTracks(any(), any(), any()) }
    verify(exactly = 0) { playlistRepository.save(any(), any()) }
  }

  @Test
  fun `syncPlaylistData proceeds normally when snapshotId matches on subsequent page`() {
    val user = buildUser()
    val nextPageUrl = "https://api.spotify.com/v1/playlists/p1/tracks?offset=50&limit=50"
    val page = buildTracksPage(snapshotId = "snap-1")
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(userId, accessToken, "p1", nextPageUrl) } returns page.right()
    every { playlistRepository.appendTracks(userId, "p1", page.tracks) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { playlistRepository.updateLastSyncTime(userId, "p1", any()) } just runs

    val result = adapter.syncPlaylistData(userId, "p1", nextPageUrl, "snap-1")

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { playlistRepository.appendTracks(userId, "p1", page.tracks) }
    verify(exactly = 0) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(userId, "p1")) }
  }


  @Test
  fun `syncPlaylistData returns Left when tracks fetch fails`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(userId, accessToken, "p1", null) } returns PlaylistSyncError.PLAYLIST_TRACKS_FETCH_FAILED.left()

    val result = adapter.syncPlaylistData(userId, "p1")

    assertThat(result.isLeft()).isTrue()
    verify(exactly = 0) { playlistRepository.save(any(), any()) }
  }

  // --- updateSyncStatus enqueue tests ---

  @Test
  fun `updateSyncStatus enqueues SyncPlaylistData when activating playlist with no existing data`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE))
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistMetadata(userId) } just runs

    val result = adapter.updateSyncStatus(userId, "p1", PlaylistSyncStatus.ACTIVE)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(userId, "p1")) }
  }

  @Test
  fun `updateSyncStatus enqueues SyncPlaylistData when activating playlist with existing data`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE))
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistMetadata(userId) } just runs

    val result = adapter.updateSyncStatus(userId, "p1", PlaylistSyncStatus.ACTIVE)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(userId, "p1")) }
    verify(exactly = 0) { outboxPort.enqueue(any<DomainOutboxEvent.RunPlaylistChecks>()) }
  }

  @Test
  fun `updateSyncStatus notifies dashboard refresh after updating sync status`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.ACTIVE))
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { playlistRepository.findByUserIdAndPlaylistId(userId, "p1") } returns mockk()
    every { dashboardRefresh.notifyUserPlaylistMetadata(userId) } just runs
    every { playlistCheckRepository.deleteByPlaylistId("p1") } just runs

    val result = adapter.updateSyncStatus(userId, "p1", PlaylistSyncStatus.PASSIVE)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { dashboardRefresh.notifyUserPlaylistMetadata(userId) }
  }

  @Test
  fun `updateSyncStatus notifies dashboard refresh when activating playlist`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE))
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { playlistRepository.findByUserIdAndPlaylistId(userId, "p1") } returns mockk()
    every { outboxPort.enqueue(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistMetadata(userId) } just runs

    val result = adapter.updateSyncStatus(userId, "p1", PlaylistSyncStatus.ACTIVE)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { dashboardRefresh.notifyUserPlaylistMetadata(userId) }
  }

  @Test
  fun `updateSyncStatus deletes check documents when deactivating playlist`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.ACTIVE))
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { playlistRepository.findByUserIdAndPlaylistId(userId, "p1") } returns mockk()
    every { dashboardRefresh.notifyUserPlaylistMetadata(userId) } just runs
    every { playlistCheckRepository.deleteByPlaylistId("p1") } just runs

    val result = adapter.updateSyncStatus(userId, "p1", PlaylistSyncStatus.PASSIVE)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { playlistCheckRepository.deleteByPlaylistId("p1") }
  }

  @Test
  fun `updateSyncStatus does not delete check documents when activating playlist`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE))
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { playlistRepository.findByUserIdAndPlaylistId(userId, "p1") } returns mockk()
    every { outboxPort.enqueue(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistMetadata(userId) } just runs

    val result = adapter.updateSyncStatus(userId, "p1", PlaylistSyncStatus.ACTIVE)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { playlistCheckRepository.deleteByPlaylistId(any()) }
  }

  @Test
  fun `updateSyncStatus sets type ALL when activating playlist named All`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE, name = "All"))
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistMetadata(userId) } just runs

    val result = adapter.updateSyncStatus(userId, "p1", PlaylistSyncStatus.ACTIVE)

    assertThat(result.isRight()).isTrue()
    val savedSlot = slot<List<PlaylistInfo>>()
    verify { playlistRepository.replaceAll(userId, capture(savedSlot)) }
    assertThat(savedSlot.captured.find { it.spotifyPlaylistId == "p1" }!!.type).isEqualTo(PlaylistType.ALL)
  }

  @Test
  fun `updateSyncStatus sets type ALL when activating playlist named all case-insensitive`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE, name = "ALL"))
    every { playlistRepository.replaceAll(any(), any()) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistMetadata(userId) } just runs

    val result = adapter.updateSyncStatus(userId, "p1", PlaylistSyncStatus.ACTIVE)

    assertThat(result.isRight()).isTrue()
    val savedSlot = slot<List<PlaylistInfo>>()
    verify { playlistRepository.replaceAll(userId, capture(savedSlot)) }
    assertThat(savedSlot.captured.find { it.spotifyPlaylistId == "p1" }!!.type).isEqualTo(PlaylistType.ALL)
  }

  // --- enqueueSyncPlaylistData tests ---

  @Test
  fun `enqueueSyncPlaylistData returns Left when user not found`() {
    every { userRepository.findById(userId) } returns null

    val result = adapter.enqueueSyncPlaylistData(userId, "p1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(PlaylistSyncError.PLAYLIST_NOT_FOUND)
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `enqueueSyncPlaylistData returns Left when playlist not found`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { playlistRepository.findByUserId(userId) } returns emptyList()

    val result = adapter.enqueueSyncPlaylistData(userId, "p1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(PlaylistSyncError.PLAYLIST_NOT_FOUND)
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `enqueueSyncPlaylistData returns Left when playlist is not active`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE))

    val result = adapter.enqueueSyncPlaylistData(userId, "p1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(PlaylistSyncError.PLAYLIST_SYNC_INACTIVE)
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `enqueueSyncPlaylistData enqueues SyncPlaylistData and returns Right`() {
    val user = buildUser()
    every { userRepository.findById(userId) } returns user
    every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1"))
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.enqueueSyncPlaylistData(userId, "p1")

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(userId, "p1")) }
  }
}
*/
