package de.chrgroth.spotify.control.domain.playback

/*
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.PlaybackError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playback.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPartialPlayedItem
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
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
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FetchCurrentlyPlayingServiceTests {

  private val userRepository: UserRepositoryPort = mockk()
  private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
  private val spotifyPlayback: SpotifyPlaybackPort = mockk(relaxed = true)
  private val currentlyPlayingRepository: CurrentlyPlayingRepositoryPort = mockk(relaxed = true)
  private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort = mockk(relaxed = true)
  private val recentlyPartialPlayedRepository: RecentlyPartialPlayedRepositoryPort = mockk(relaxed = true)
  private val appPlaybackRepository: AppPlaybackRepositoryPort = mockk(relaxed = true)
  private val syncController: SyncController = mockk(relaxed = true)
  private val outboxPort: OutboxPort = mockk(relaxed = true)
  private val dashboardRefresh: DashboardRefreshPort = mockk(relaxed = true)
  private val playbackState: PlaybackStatePort = mockk(relaxed = true)

  private val service = PlaybackService(
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

  // --- basic fetch tests ---

  @Test
  fun `fetchCurrentlyPlaying returns Left on error`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlayback.getCurrentlyPlaying(userId, accessToken) } returns PlaybackError.CURRENTLY_PLAYING_FETCH_FAILED.left()

    val result = service.fetchCurrentlyPlaying(userId)

    assertThat(result.isLeft()).isTrue()
    verify(exactly = 0) { currentlyPlayingRepository.save(any()) }
  }

  @Test
  fun `fetchCurrentlyPlaying does nothing when nothing is playing`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlayback.getCurrentlyPlaying(userId, accessToken) } returns null.right()

    service.fetchCurrentlyPlaying(userId)

    verify(exactly = 0) { currentlyPlayingRepository.save(any()) }
    verify(exactly = 0) { currentlyPlayingRepository.updateProgress(any()) }
    verify(exactly = 0) { dashboardRefresh.notifyUserPlaybackData(any()) }
  }

  @Test
  fun `fetchCurrentlyPlaying saves new entry when no existing entry for track`() {
    val item = currentlyPlayingItem("track-1", progressMs = 30_000L)
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlayback.getCurrentlyPlaying(userId, accessToken) } returns item.right()
    every { currentlyPlayingRepository.findMostRecentByUserAndTrack(userId, item.trackId) } returns null

    service.fetchCurrentlyPlaying(userId)

    verify { currentlyPlayingRepository.save(item) }
    verify(exactly = 0) { currentlyPlayingRepository.updateProgress(any()) }
  }

  @Test
  fun `fetchCurrentlyPlaying updates existing entry and preserves original start time`() {
    val startTime = now - 2.minutes
    val existingItem = currentlyPlayingItem("track-1", progressMs = 60_000L, observedAt = now - 20.minutes)
      .copy(startTime = startTime)
    val newItem = currentlyPlayingItem("track-1", progressMs = 80_000L)

    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlayback.getCurrentlyPlaying(userId, accessToken) } returns newItem.right()
    every { currentlyPlayingRepository.findMostRecentByUserAndTrack(userId, newItem.trackId) } returns existingItem

    service.fetchCurrentlyPlaying(userId)

    verify(exactly = 0) { currentlyPlayingRepository.save(any()) }
    verify { currentlyPlayingRepository.updateProgress(newItem.copy(startTime = startTime)) }
  }

  @Test
  fun `fetchCurrentlyPlaying updates across minute boundary without creating new entry`() {
    val startTime = now - 5.minutes
    val existingItem = currentlyPlayingItem("track-1", progressMs = 200_000L, observedAt = now - 61.minutes)
      .copy(startTime = startTime)
    val newItem = currentlyPlayingItem("track-1", progressMs = 210_000L)

    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlayback.getCurrentlyPlaying(userId, accessToken) } returns newItem.right()
    every { currentlyPlayingRepository.findMostRecentByUserAndTrack(userId, newItem.trackId) } returns existingItem

    service.fetchCurrentlyPlaying(userId)

    verify(exactly = 0) { currentlyPlayingRepository.save(any()) }
    verify { currentlyPlayingRepository.updateProgress(newItem.copy(startTime = startTime)) }
  }

  @Test
  fun `fetchCurrentlyPlaying treats pause-resume as same session and preserves start time`() {
    val originalStartTime = now - 10.minutes
    // Track at 2 minutes progress before pause
    val existingItem = currentlyPlayingItem("track-1", progressMs = 120_000L, observedAt = now - 5.minutes)
      .copy(startTime = originalStartTime)
    // After resuming — progress unchanged, but observedAt moved forward; recalculated start time would differ
    val afterResumeItem = currentlyPlayingItem("track-1", progressMs = 120_500L)

    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlayback.getCurrentlyPlaying(userId, accessToken) } returns afterResumeItem.right()
    every { currentlyPlayingRepository.findMostRecentByUserAndTrack(userId, afterResumeItem.trackId) } returns existingItem

    service.fetchCurrentlyPlaying(userId)

    // Must update with original start time, not the drifted one
    verify { currentlyPlayingRepository.updateProgress(afterResumeItem.copy(startTime = originalStartTime)) }
    verify(exactly = 0) { currentlyPlayingRepository.save(any()) }
  }

  @Test
  fun `fetchCurrentlyPlaying detects track restart and saves new entry`() {
    // Existing entry: track was 2 minutes in
    val existingItem = currentlyPlayingItem("track-1", progressMs = 120_000L, observedAt = now - 5.minutes)
    // New observation: track restarted, only 3 seconds in
    val restartedItem = currentlyPlayingItem("track-1", progressMs = 3_000L)

    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlayback.getCurrentlyPlaying(userId, accessToken) } returns restartedItem.right()
    every { currentlyPlayingRepository.findMostRecentByUserAndTrack(userId, restartedItem.trackId) } returns existingItem

    service.fetchCurrentlyPlaying(userId)

    verify { currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, setOf("track-1")) }
    verify { currentlyPlayingRepository.save(restartedItem) }
    verify(exactly = 0) { currentlyPlayingRepository.updateProgress(any()) }
  }

  @Test
  fun `fetchCurrentlyPlaying does not treat low-progress first play as restart when no prior entry exists`() {
    val newItem = currentlyPlayingItem("track-1", progressMs = 3_000L)

    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlayback.getCurrentlyPlaying(userId, accessToken) } returns newItem.right()
    every { currentlyPlayingRepository.findMostRecentByUserAndTrack(userId, newItem.trackId) } returns null

    service.fetchCurrentlyPlaying(userId)

    verify { currentlyPlayingRepository.save(newItem) }
    verify(exactly = 0) { currentlyPlayingRepository.deleteByUserIdAndTrackIds(any(), any()) }
    verify(exactly = 0) { currentlyPlayingRepository.updateProgress(any()) }
  }

  // --- orphan cleanup tests ---

  @Test
  fun `fetchCurrentlyPlaying converts orphaned entry with sufficient progress to partial play`() {
    val trackB = currentlyPlayingItem("track-b", progressMs = 60_000L)
    val orphanedTrackA = currentlyPlayingItem("track-a", progressMs = 50_000L, observedAt = now - 5.minutes)
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlayback.getCurrentlyPlaying(userId, accessToken) } returns trackB.right()
    every { currentlyPlayingRepository.findMostRecentByUserAndTrack(userId, trackB.trackId) } returns null
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(orphanedTrackA)
    every { recentlyPartialPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.saveAll(any()) } just runs

    service.fetchCurrentlyPlaying(userId)

    val savedSlot = slot<List<RecentlyPartialPlayedItem>>()
    verify { recentlyPartialPlayedRepository.saveAll(capture(savedSlot)) }
    assertThat(savedSlot.captured).hasSize(1)
    assertThat(savedSlot.captured[0].trackId).isEqualTo(TrackId("track-a"))
    assertThat(savedSlot.captured[0].playedSeconds).isEqualTo(50L)
    verify { currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, setOf("track-a")) }
  }

  @Test
  fun `fetchCurrentlyPlaying deletes orphaned entry below progress threshold without creating partial play`() {
    val trackB = currentlyPlayingItem("track-b", progressMs = 60_000L)
    val orphanedTrackA = currentlyPlayingItem("track-a", progressMs = 5_000L, observedAt = now - 5.minutes)
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlayback.getCurrentlyPlaying(userId, accessToken) } returns trackB.right()
    every { currentlyPlayingRepository.findMostRecentByUserAndTrack(userId, trackB.trackId) } returns null
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(orphanedTrackA)

    service.fetchCurrentlyPlaying(userId)

    verify(exactly = 0) { recentlyPartialPlayedRepository.saveAll(any()) }
    verify { currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, setOf("track-a")) }
  }

  @Test
  fun `fetchCurrentlyPlaying preserves first Track A play when returning to Track A after Track B`() {
    // Simulate: Track A (50% played) → Track B → back to Track A
    // When Track B is detected, Track A's entry must be converted to a partial play before deletion
    val trackA = currentlyPlayingItem("track-a", progressMs = 150_000L, observedAt = now - 10.minutes)
    val trackB = currentlyPlayingItem("track-b", progressMs = 60_000L)
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlayback.getCurrentlyPlaying(userId, accessToken) } returns trackB.right()
    every { currentlyPlayingRepository.findMostRecentByUserAndTrack(userId, trackB.trackId) } returns null
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(trackA)
    every { recentlyPartialPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.saveAll(any()) } just runs

    service.fetchCurrentlyPlaying(userId)

    // Track A is converted to a partial play
    val savedSlot = slot<List<RecentlyPartialPlayedItem>>()
    verify { recentlyPartialPlayedRepository.saveAll(capture(savedSlot)) }
    assertThat(savedSlot.captured[0].trackId).isEqualTo(TrackId("track-a"))
    assertThat(savedSlot.captured[0].startTime).isEqualTo(trackA.startTime)
    // Track A's currently playing entry is deleted
    verify { currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, setOf("track-a")) }
  }

  @Test
  fun `fetchCurrentlyPlaying cleans up orphaned entries when nothing is playing`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlayback.getCurrentlyPlaying(userId, accessToken) } returns null.right()
    every { currentlyPlayingRepository.findByUserId(userId) } returns emptyList()

    service.fetchCurrentlyPlaying(userId)

    verify { currentlyPlayingRepository.findByUserId(userId) }
    verify(exactly = 0) { recentlyPartialPlayedRepository.saveAll(any()) }
    verify(exactly = 0) { currentlyPlayingRepository.deleteByUserIdAndTrackIds(any(), any()) }
  }

  @Test
  fun `fetchCurrentlyPlaying converts and deletes orphaned entries when nothing is playing`() {
    val lingeringTrack = currentlyPlayingItem("track-a", progressMs = 50_000L, observedAt = now - 5.minutes)
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlayback.getCurrentlyPlaying(userId, accessToken) } returns null.right()
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(lingeringTrack)
    every { recentlyPartialPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
    every { recentlyPartialPlayedRepository.saveAll(any()) } just runs

    service.fetchCurrentlyPlaying(userId)

    val savedSlot = slot<List<RecentlyPartialPlayedItem>>()
    verify { recentlyPartialPlayedRepository.saveAll(capture(savedSlot)) }
    assertThat(savedSlot.captured).hasSize(1)
    assertThat(savedSlot.captured[0].trackId).isEqualTo(TrackId("track-a"))
    assertThat(savedSlot.captured[0].playedSeconds).isEqualTo(50L)
    verify { currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, setOf("track-a")) }
  }

  @Test
  fun `fetchCurrentlyPlaying deletes orphaned entry below progress threshold without creating partial play when nothing is playing`() {
    val lingeringTrack = currentlyPlayingItem("track-a", progressMs = 5_000L, observedAt = now - 5.minutes)
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlayback.getCurrentlyPlaying(userId, accessToken) } returns null.right()
    every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(lingeringTrack)

    service.fetchCurrentlyPlaying(userId)

    verify(exactly = 0) { recentlyPartialPlayedRepository.saveAll(any()) }
    verify { currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, setOf("track-a")) }
  }

  // --- playback state and dashboard notifications ---

  @Test
  fun `fetchCurrentlyPlaying notifies playback state when track is playing`() {
    val item = currentlyPlayingItem("track-1", progressMs = 30_000L)
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlayback.getCurrentlyPlaying(userId, accessToken) } returns item.right()
    every { currentlyPlayingRepository.findMostRecentByUserAndTrack(userId, item.trackId) } returns null

    service.fetchCurrentlyPlaying(userId)

    verify { playbackState.onPlaybackDetected() }
  }

  @Test
  fun `fetchCurrentlyPlaying does not notify playback state when item is paused`() {
    val item = currentlyPlayingItem("track-1", progressMs = 30_000L).copy(isPlaying = false)
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlayback.getCurrentlyPlaying(userId, accessToken) } returns item.right()
    every { currentlyPlayingRepository.findMostRecentByUserAndTrack(userId, item.trackId) } returns null

    service.fetchCurrentlyPlaying(userId)

    verify(exactly = 0) { playbackState.onPlaybackDetected() }
  }

  @Test
  fun `fetchCurrentlyPlaying notifies dashboard when track is detected`() {
    val item = currentlyPlayingItem("track-1", progressMs = 30_000L)
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlayback.getCurrentlyPlaying(userId, accessToken) } returns item.right()
    every { currentlyPlayingRepository.findMostRecentByUserAndTrack(userId, item.trackId) } returns null

    service.fetchCurrentlyPlaying(userId)

    verify { dashboardRefresh.notifyUserPlaybackData(userId) }
  }

  @Test
  fun `fetchCurrentlyPlaying does not notify dashboard when nothing is playing`() {
    every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    every { spotifyPlayback.getCurrentlyPlaying(userId, accessToken) } returns null.right()

    service.fetchCurrentlyPlaying(userId)

    verify(exactly = 0) { dashboardRefresh.notifyUserPlaybackData(any()) }
  }
}
*/
