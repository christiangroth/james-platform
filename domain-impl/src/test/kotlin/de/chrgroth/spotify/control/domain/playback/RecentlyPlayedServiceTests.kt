package de.chrgroth.spotify.control.domain.playback

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.PlaybackError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.playback.AppPlaybackItem
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playback.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPartialPlayedItem
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.User
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.playback.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.CurrentlyPlayingRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackStatePort
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPartialPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.playback.SpotifyPlaybackPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import de.chrgroth.spotify.control.domain.catalog.SyncController
import de.chrgroth.spotify.control.domain.catalog.CatalogSyncRequest
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RecentlyPlayedServiceTests {

  private val userRepository: UserRepositoryPort = mockk()
  private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
  private val spotifyPlayback: SpotifyPlaybackPort = mockk(relaxed = true)
  private val currentlyPlayingRepository: CurrentlyPlayingRepositoryPort = mockk(relaxed = true)
  private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort = mockk()
  private val recentlyPartialPlayedRepository: RecentlyPartialPlayedRepositoryPort = mockk(relaxed = true)
  private val appPlaybackRepository: AppPlaybackRepositoryPort = mockk(relaxed = true)
  private val syncController: SyncController = mockk(relaxed = true)
  private val outboxPort: OutboxPort = mockk(relaxed = true)
  private val dashboardRefresh: DashboardRefreshPort = mockk(relaxed = true)
  private val playbackState: PlaybackStatePort = mockk(relaxed = true)

  private val adapter = PlaybackService(
    userRepository,
    spotifyAccessToken,
    spotifyPlayback,
    currentlyPlayingRepository,
    recentlyPlayedRepository,
    recentlyPartialPlayedRepository,
    appPlaybackRepository,
    syncController,
    outboxPort,
    dashboardRefresh,
    playbackState,
    minimumProgressSeconds = 25L,
  )

  private val userId = UserId("user-1")
  private val accessToken = AccessToken("token")
  private val now = Clock.System.now()

  private fun buildUser(id: String) = User(
    spotifyUserId = UserId(id),
    displayName = "User $id",
    encryptedAccessToken = "enc-access",
    encryptedRefreshToken = "enc-refresh",
    tokenExpiresAt = now + 1.hours,
    lastLoginAt = now,
  )

  private fun item(index: Int, forUserId: UserId = userId, albumId: String? = null) = RecentlyPlayedItem(
    spotifyUserId = forUserId,
    trackId = TrackId("track-$index"),
    trackName = "Track $index",
    artistIds = listOf(ArtistId("artist-id-$index")),
    artistNames = listOf("Artist $index"),
    playedAt = now - index.hours,
    albumId = albumId?.let { AlbumId(it) },
  )

  private fun currentlyPlayingItem(
    trackId: String,
    progressMs: Long,
    observedAt: Instant = now,
    durationMs: Long = 600_000L,
  ) = CurrentlyPlayingItem(
    spotifyUserId = userId,
    trackId = TrackId(trackId),
    trackName = "Track $trackId",
    artistIds = listOf(ArtistId("artist-$trackId")),
    artistNames = listOf("Artist $trackId"),
    progressMs = progressMs,
    durationMs = durationMs,
    isPlaying = true,
    observedAt = observedAt,
    startTime = observedAt - progressMs.milliseconds,
  )

  private fun setupNoConsolidation() {
    every { currentlyPlayingRepository.findByUserId(userId) } returns emptyList()
  }

  // --- enqueueUpdates tests ---

  @Test
  fun `enqueueUpdates does nothing when no users exist`() {
    every { userRepository.findAll() } returns emptyList()

    adapter.enqueueFetchPlaybackData()

    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `enqueueUpdates enqueues one task per user`() {
    every { userRepository.findAll() } returns listOf(buildUser("user-1"), buildUser("user-2"))
    every { outboxPort.enqueue(any()) } just runs

    adapter.enqueueFetchPlaybackData()

    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.FetchPlaybackData(UserId("user-1"))) }
    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.FetchPlaybackData(UserId("user-2"))) }
  }

  // --- update tests ---

  @Test
  fun `update persists new tracks`() {
    val items = listOf(item(1), item(2))
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns items.right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPlayedRepository.saveAll(any()) } just runs
    setupNoConsolidation()

    adapter.fetchRecentlyPlayed(userId)

    val savedSlot = slot<List<RecentlyPlayedItem>>()
    verify { recentlyPlayedRepository.saveAll(capture(savedSlot)) }
    assertThat(savedSlot.captured).hasSize(2)
    assertThat(savedSlot.captured.map { it.trackId }).containsExactlyInAnyOrder(TrackId("track-1"), TrackId("track-2"))
  }

  @Test
  fun `update notifies playback data when new items are persisted`() {
    val items = listOf(item(1))
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns items.right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPlayedRepository.saveAll(any()) } just runs
    setupNoConsolidation()

    adapter.fetchRecentlyPlayed(userId)

    verify { dashboardRefresh.notifyUserPlaybackData(userId) }
  }

  @Test
  fun `update does not notify playback data when no new items exist`() {
    val items = listOf(item(1))
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns items.right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns setOf(items[0].playedAt)
    setupNoConsolidation()

    adapter.fetchRecentlyPlayed(userId)

    verify(exactly = 0) { dashboardRefresh.notifyUserPlaybackData(any()) }
  }

  @Test
  fun `update passes most recent playedAt as after cursor`() {
    val after = now - 2.hours
    val items = listOf(item(1))
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns after
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, after) } returns items.right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPlayedRepository.saveAll(any()) } just runs
    setupNoConsolidation()

    adapter.fetchRecentlyPlayed(userId)

    verify { spotifyPlayback.getRecentlyPlayed(userId, accessToken, after) }
  }

  @Test
  fun `update skips duplicate tracks`() {
    val items = listOf(item(1), item(2))
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns items.right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns setOf(items[0].playedAt)
    every { recentlyPlayedRepository.saveAll(any()) } just runs
    setupNoConsolidation()

    adapter.fetchRecentlyPlayed(userId)

    val savedSlot = slot<List<RecentlyPlayedItem>>()
    verify { recentlyPlayedRepository.saveAll(capture(savedSlot)) }
    assertThat(savedSlot.captured).hasSize(1)
    assertThat(savedSlot.captured[0].trackId).isEqualTo(TrackId("track-2"))
  }

  @Test
  fun `update does not call saveAll when all tracks are duplicates`() {
    val items = listOf(item(1))
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns items.right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns setOf(items[0].playedAt)
    setupNoConsolidation()

    adapter.fetchRecentlyPlayed(userId)

    verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
  }

  @Test
  fun `update does not call saveAll when no tracks returned`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    setupNoConsolidation()

    adapter.fetchRecentlyPlayed(userId)

    verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
  }

  @Test
  fun `update returns Left on domain error`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns PlaybackError.RECENTLY_PLAYED_FETCH_FAILED.left()

    val result = adapter.fetchRecentlyPlayed(userId)

    assertThat(result.isLeft()).isTrue()
    verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
  }

  // --- consolidation tests ---

  @Test
  fun `update deletes currently playing entries for completed tracks at end`() {
    val items = listOf(item(1))
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns items.right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPlayedRepository.saveAll(any()) } just runs
    setupNoConsolidation()

    adapter.fetchRecentlyPlayed(userId)

    verify { currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, setOf("track-1")) }
  }

  @Test
  fun `update converts partial plays exceeding 25s to recently partial played`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.saveAll(any()) } just runs

    val olderItem = currentlyPlayingItem("track-old", progressMs = 50_000L, observedAt = now - 4.minutes)
    val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderItem, latestTrack)

    adapter.fetchRecentlyPlayed(userId)

    val savedSlot = slot<List<RecentlyPartialPlayedItem>>()
    verify { recentlyPartialPlayedRepository.saveAll(capture(savedSlot)) }
    assertThat(savedSlot.captured).hasSize(1)
    assertThat(savedSlot.captured[0].trackId).isEqualTo(TrackId("track-old"))
  }

  @Test
  fun `update computes playedSeconds using progressMs when another track was observed next`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.saveAll(any()) } just runs

    val olderItem = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 5.minutes)
    val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now - 1.minutes)
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderItem, latestTrack)

    adapter.fetchRecentlyPlayed(userId)

    val savedSlot = slot<List<RecentlyPartialPlayedItem>>()
    verify { recentlyPartialPlayedRepository.saveAll(capture(savedSlot)) }
    assertThat(savedSlot.captured[0].playedSeconds).isEqualTo(30_000L / 1_000L)
  }

  @Test
  fun `update converts each item independently to a separate partial played record`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.saveAll(any()) } just runs

    // Three observations of the same track — each becomes its own partial play record
    val olderFirst = currentlyPlayingItem("track-old", progressMs = 35_000L, observedAt = now - 6.minutes)
    val olderSecond = currentlyPlayingItem("track-old", progressMs = 45_000L, observedAt = now - 4.minutes)
    val olderThird = currentlyPlayingItem("track-old", progressMs = 50_000L, observedAt = now - 2.minutes)
    val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderFirst, olderSecond, olderThird, latestTrack)

    adapter.fetchRecentlyPlayed(userId)

    val savedSlot = slot<List<RecentlyPartialPlayedItem>>()
    verify { recentlyPartialPlayedRepository.saveAll(capture(savedSlot)) }
    // Each observation above the minimum threshold produces its own record
    assertThat(savedSlot.captured).hasSize(3)
    assertThat(savedSlot.captured.map { it.playedSeconds }).containsExactlyInAnyOrder(35L, 45L, 50L)
  }

  @Test
  fun `update computes playedSeconds using progressMs regardless of observation gap to next track`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.saveAll(any()) } just runs

    // track-old was briefly played hours ago; a different track is observed much later.
    // The large observation gap is irrelevant — playedSeconds is based on progressMs only.
    val trackDurationMs = 210_000L // 3.5 minutes
    val olderItem = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 12.hours, durationMs = trackDurationMs)
    val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderItem, latestTrack)

    adapter.fetchRecentlyPlayed(userId)

    val savedSlot = slot<List<RecentlyPartialPlayedItem>>()
    verify { recentlyPartialPlayedRepository.saveAll(capture(savedSlot)) }
    // progressMs = 30s, well within track duration — no cap needed
    assertThat(savedSlot.captured[0].playedSeconds).isEqualTo(30_000L / 1_000L)
  }

  @Test
  fun `update caps playedSeconds at track durationMs when progressMs exceeds track length`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.saveAll(any()) } just runs

    // Spotify returned a progressMs larger than the track's durationMs (bad data);
    // the observation gap also exceeds durationMs — result must be capped at track duration
    val trackDurationMs = 210_000L // 3.5 minutes
    val olderItem = currentlyPlayingItem("track-old", progressMs = 999_000L, observedAt = now - 5.minutes, durationMs = trackDurationMs)
    val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderItem, latestTrack)

    adapter.fetchRecentlyPlayed(userId)

    val savedSlot = slot<List<RecentlyPartialPlayedItem>>()
    verify { recentlyPartialPlayedRepository.saveAll(capture(savedSlot)) }
    // observation gap = 5 min = 300s and progressMs = 999s both exceed durationMs of 210s; must be capped
    assertThat(savedSlot.captured[0].playedSeconds).isEqualTo(trackDurationMs / 1_000L)
  }

  @Test
  fun `update does not convert the sole non-completed track even when completed tracks exist`() {
    val completedItems = listOf(item(1))
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns completedItems.right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPlayedRepository.saveAll(any()) } just runs

    val olderItem = currentlyPlayingItem("track-old", progressMs = 45_000L, observedAt = now - 5.minutes)
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderItem)

    adapter.fetchRecentlyPlayed(userId)

    verify(exactly = 0) { recentlyPartialPlayedRepository.saveAll(any()) }
  }

  @Test
  fun `update converts each item using its own progressMs even when later track is completed`() {
    val completedItems = listOf(item(1))
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns completedItems.right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPlayedRepository.saveAll(any()) } just runs
    every { recentlyPartialPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.saveAll(any()) } just runs

    val olderTrackFirst = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 8.minutes)
    val olderTrackSecond = currentlyPlayingItem("track-old", progressMs = 45_000L, observedAt = now - 6.minutes)
    val completedEntry = currentlyPlayingItem("track-1", progressMs = 5_000L, observedAt = now - 4.minutes)
    val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now - 2.minutes)
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrackFirst, olderTrackSecond, completedEntry, latestTrack)

    adapter.fetchRecentlyPlayed(userId)

    val savedSlot = slot<List<RecentlyPartialPlayedItem>>()
    verify { recentlyPartialPlayedRepository.saveAll(capture(savedSlot)) }
    // Both track-old observations are converted independently; completedEntry is deleted (completed), not converted
    val trackOldItems = savedSlot.captured.filter { it.trackId == TrackId("track-old") }
    assertThat(trackOldItems).hasSize(2)
    assertThat(trackOldItems.map { it.playedSeconds }).containsExactlyInAnyOrder(30L, 45L)
  }

  @Test
  fun `update creates two partial played items when same track played twice`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.saveAll(any()) } just runs

    // First play of track-a: one observation below threshold, one above
    val session1First = currentlyPlayingItem("track-a", progressMs = 5_000L, observedAt = now - 10.minutes)
    val session1Second = currentlyPlayingItem("track-a", progressMs = 30_000L, observedAt = now - 9.minutes)
    // Different track played in between
    val differentTrack = currentlyPlayingItem("track-b", progressMs = 30_000L, observedAt = now - 7.minutes)
    // Second play of track-a from beginning
    val session2First = currentlyPlayingItem("track-a", progressMs = 5_000L, observedAt = now - 6.minutes)
    val session2Second = currentlyPlayingItem("track-a", progressMs = 30_000L, observedAt = now - 5.minutes)
    // track-latest is the protected item
    val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(
      session1First, session1Second, differentTrack, session2First, session2Second, latestTrack,
    )

    adapter.fetchRecentlyPlayed(userId)

    val savedSlot = slot<List<RecentlyPartialPlayedItem>>()
    verify { recentlyPartialPlayedRepository.saveAll(capture(savedSlot)) }
    // Both above-threshold observations of track-a become their own partial play records
    val trackAItems = savedSlot.captured.filter { it.trackId == TrackId("track-a") }
    assertThat(trackAItems).hasSize(2)
    assertThat(trackAItems.map { it.playedAt }).containsExactlyInAnyOrder(
      now - 9.minutes,
      now - 5.minutes,
    )
  }

  @Test
  fun `update creates two partial played items when same track played twice consecutively with progress reset`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.saveAll(any()) } just runs

    // First play of track-a
    val firstPlay1 = currentlyPlayingItem("track-a", progressMs = 5_000L, observedAt = now - 10.minutes)
    val firstPlay2 = currentlyPlayingItem("track-a", progressMs = 30_000L, observedAt = now - 9.minutes)
    // track-a restarted immediately: progress drops back to 5s (no different track in between)
    val secondPlay1 = currentlyPlayingItem("track-a", progressMs = 5_000L, observedAt = now - 8.minutes)
    val secondPlay2 = currentlyPlayingItem("track-a", progressMs = 30_000L, observedAt = now - 7.minutes)
    // track-latest is the protected item
    val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(
      firstPlay1, firstPlay2, secondPlay1, secondPlay2, latestTrack,
    )

    adapter.fetchRecentlyPlayed(userId)

    val savedSlot = slot<List<RecentlyPartialPlayedItem>>()
    verify { recentlyPartialPlayedRepository.saveAll(capture(savedSlot)) }
    val trackAItems = savedSlot.captured.filter { it.trackId == TrackId("track-a") }
    assertThat(trackAItems).hasSize(2)
    assertThat(trackAItems.map { it.playedAt }).containsExactlyInAnyOrder(
      now - 9.minutes,
      now - 7.minutes,
    )
  }

  @Test
  fun `update does not convert the latest currently playing item`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()

    val latestTrack = currentlyPlayingItem("track-latest", progressMs = 60_000L, observedAt = now)
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(latestTrack)

    adapter.fetchRecentlyPlayed(userId)

    verify(exactly = 0) { recentlyPartialPlayedRepository.saveAll(any()) }
  }

  @Test
  fun `update does not convert partial plays shorter than minimum progress but still deletes them`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()

    val olderTrack = currentlyPlayingItem("track-old", progressMs = 10_000L, observedAt = now - 5.minutes)
    val latestTrack = currentlyPlayingItem("track-latest", progressMs = 5_000L, observedAt = now)
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrack, latestTrack)

    adapter.fetchRecentlyPlayed(userId)

    verify(exactly = 0) { recentlyPartialPlayedRepository.saveAll(any()) }
    verify { currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, setOf("track-old")) }
  }

  @Test
  fun `update notifies dashboard when partial plays are converted`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.saveAll(any()) } just runs

    val olderTrack = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 5.minutes)
    val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrack, latestTrack)

    adapter.fetchRecentlyPlayed(userId)

    verify { dashboardRefresh.notifyUserPlaybackData(userId) }
  }

  @Test
  fun `update deletes converted currently playing entries at end`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.saveAll(any()) } just runs

    val olderTrack = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 5.minutes)
    val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrack, latestTrack)

    adapter.fetchRecentlyPlayed(userId)

    verify { currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, setOf("track-old")) }
  }

  @Test
  fun `update deletes both completed and converted tracks together at end`() {
    val completedItems = listOf(item(1))
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns completedItems.right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPlayedRepository.saveAll(any()) } just runs
    every { recentlyPartialPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.saveAll(any()) } just runs

    val olderTrack = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 5.minutes)
    val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrack, latestTrack)

    adapter.fetchRecentlyPlayed(userId)

    verify { currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, setOf("track-1", "track-old")) }
  }

  @Test
  fun `update does not delete latest item trackId even when older items of same track are converted`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.saveAll(any()) } just runs

    // session1 of track-a is eligible; different (track-b, below threshold) is not converted; session2 of track-a is the latest (protected)
    val session1 = currentlyPlayingItem("track-a", progressMs = 30_000L, observedAt = now - 6.minutes)
    val different = currentlyPlayingItem("track-b", progressMs = 10_000L, observedAt = now - 4.minutes)
    val session2 = currentlyPlayingItem("track-a", progressMs = 5_000L, observedAt = now)
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(session1, different, session2)

    adapter.fetchRecentlyPlayed(userId)

    // track-a must NOT be deleted (session2 is the latest item, its trackId is protected)
    // track-b IS deleted even though it's below the conversion threshold — it must not accumulate forever
    verify { currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, setOf("track-b")) }
  }

  @Test
  fun `update saves partial played item with first observedAt as playedAt`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.saveAll(any()) } just runs

    val firstObserved = now - 5.minutes
    val olderTrack = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = firstObserved)
    val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrack, latestTrack)

    adapter.fetchRecentlyPlayed(userId)

    val savedSlot = slot<List<RecentlyPartialPlayedItem>>()
    verify { recentlyPartialPlayedRepository.saveAll(capture(savedSlot)) }
    assertThat(savedSlot.captured[0].playedAt).isEqualTo(firstObserved)
  }

  @Test
  fun `update does not save partial played item to recently played repository`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
    every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
    every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.saveAll(any()) } just runs

    val olderTrack = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 5.minutes)
    val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrack, latestTrack)

    adapter.fetchRecentlyPlayed(userId)

    verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
  }

  // --- appendPlaybackData tests ---

  private fun setupAppendPlaybackData(
    recentlyPlayed: List<RecentlyPlayedItem> = emptyList(),
    partialPlayed: List<RecentlyPartialPlayedItem> = emptyList(),
  ) {
    every { appPlaybackRepository.findMostRecentPlayedAt(userId) } returns null
    every { recentlyPlayedRepository.findSince(userId, null) } returns recentlyPlayed
    every { recentlyPartialPlayedRepository.findSince(userId, null) } returns partialPlayed
    every { appPlaybackRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { appPlaybackRepository.saveAll(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs
  }

  @Test
  fun `appendPlaybackData sets secondsPlayed from durationSeconds of recently played item`() {
    val recentlyPlayedItem = item(1).copy(durationSeconds = 210L)
    setupAppendPlaybackData(recentlyPlayed = listOf(recentlyPlayedItem))

    adapter.appendPlaybackData(userId)

    val savedSlot = slot<List<AppPlaybackItem>>()
    verify { appPlaybackRepository.saveAll(capture(savedSlot)) }
    assertThat(savedSlot.captured).hasSize(1)
    assertThat(savedSlot.captured[0].secondsPlayed).isEqualTo(210L)
  }

  @Test
  fun `appendPlaybackData sets secondsPlayed to zero when durationSeconds is null`() {
    val recentlyPlayedItem = item(1).copy(durationSeconds = null)
    setupAppendPlaybackData(recentlyPlayed = listOf(recentlyPlayedItem))

    adapter.appendPlaybackData(userId)

    val savedSlot = slot<List<AppPlaybackItem>>()
    verify { appPlaybackRepository.saveAll(capture(savedSlot)) }
    assertThat(savedSlot.captured).hasSize(1)
    assertThat(savedSlot.captured[0].secondsPlayed).isEqualTo(0L)
  }

  @Test
  fun `appendPlaybackData delegates catalog sync to syncController`() {
    val recentlyPlayedItem = item(1, albumId = "album-1")
    setupAppendPlaybackData(recentlyPlayed = listOf(recentlyPlayedItem))

    adapter.appendPlaybackData(userId)

    verify {
      syncController.syncForTracks(
        listOf(CatalogSyncRequest("track-1", listOf("artist-id-1"))),
        userId,
      )
    }
  }

  @Test
  fun `appendPlaybackData passes all filtered tracks to syncController`() {
    val item1 = item(1, albumId = "album-1")
    val item2 = item(2, albumId = "album-2")
    setupAppendPlaybackData(recentlyPlayed = listOf(item1, item2))

    adapter.appendPlaybackData(userId)

    verify {
      syncController.syncForTracks(
        match { requests ->
          requests.size == 2 &&
            requests.any { it.trackId == "track-1" } &&
            requests.any { it.trackId == "track-2" }
        },
        userId,
      )
    }
  }

  @Test
  fun `appendPlaybackData deduplicates tracks by trackId when passing to syncController`() {
    val item1 = item(1, albumId = "album-shared")
    val item2 = item(2, albumId = "album-shared")
    setupAppendPlaybackData(recentlyPlayed = listOf(item1, item2))

    adapter.appendPlaybackData(userId)

    verify {
      syncController.syncForTracks(
        match { requests -> requests.size == 2 },
        userId,
      )
    }
  }

  @Test
  fun `appendPlaybackData passes track with no albumId to syncController`() {
    val recentlyPlayedItem = item(1) // no albumId
    setupAppendPlaybackData(recentlyPlayed = listOf(recentlyPlayedItem))

    adapter.appendPlaybackData(userId)

    verify {
      syncController.syncForTracks(
        listOf(CatalogSyncRequest("track-1", listOf("artist-id-1"))),
        userId,
      )
    }
  }
}

