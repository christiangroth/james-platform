package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.DashboardStats
import de.chrgroth.spotify.control.domain.model.playback.DayCount
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.playback.ListeningStats
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckStats
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.playback.TopEntry
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogBrowserPort
import de.chrgroth.spotify.control.domain.port.`in`.infra.DashboardPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class DashboardService(
  private val appPlaybackRepository: AppPlaybackRepositoryPort,
  private val appTrackRepository: AppTrackRepositoryPort,
  private val appArtistRepository: AppArtistRepositoryPort,
  private val appAlbumRepository: AppAlbumRepositoryPort,
  private val catalogBrowser: CatalogBrowserPort,
  private val playlistRepository: PlaylistRepositoryPort,
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort,
  @param:ConfigProperty(name = "dashboard.recently-played.limit")
  private val recentlyPlayedLimit: Int,
  @param:ConfigProperty(name = "dashboard.listening-stats.top-entries-limit")
  private val topEntriesLimit: Int,
) : DashboardPort {

  override fun getStats(userId: UserId): DashboardStats {
    val since = Clock.System.now() - STATS_DAYS.days
    return runBlocking {
      val playbackStatsAsync = async(Dispatchers.IO) { computePlaybackStats(userId, since) }
      val playlistMetadataAsync = async(Dispatchers.IO) { computePlaylistMetadata(userId) }
      val playlistCheckStatsAsync = async(Dispatchers.IO) { computePlaylistCheckStats() }
      val recentlyPlayedAsync = async(Dispatchers.IO) { buildRecentlyPlayedTracks(userId) }
      val listeningStatsAsync = async(Dispatchers.IO) { buildListeningStats(userId, since) }
      val catalogStatsAsync = async(Dispatchers.IO) { catalogBrowser.getCatalogStats() }
      val playbackStats = playbackStatsAsync.await()
      val playlistMetadata = playlistMetadataAsync.await()
      DashboardStats(
        syncedPlaylists = playlistMetadata.syncedPlaylists,
        totalPlaylists = playlistMetadata.totalPlaylists,
        playlistCheckStats = playlistCheckStatsAsync.await(),
        totalPlaybackEvents = playbackStats.totalPlaybackEvents,
        playbackEventsLast30Days = playbackStats.playbackEventsLast30Days,
        playbackEventsPerDay = playbackStats.playbackEventsPerDay,
        recentlyPlayedTracks = recentlyPlayedAsync.await(),
        listeningStats = listeningStatsAsync.await(),
        catalogStats = catalogStatsAsync.await(),
      )
    }
  }

  override fun getPlaybackStats(userId: UserId): DashboardStats {
    val since = Clock.System.now() - STATS_DAYS.days
    return computePlaybackStats(userId, since)
  }

  override fun getPlaylistMetadata(userId: UserId): DashboardStats = computePlaylistMetadata(userId)

  override fun getRecentlyPlayed(userId: UserId): DashboardStats =
    DashboardStats.EMPTY.copy(recentlyPlayedTracks = runBlocking { buildRecentlyPlayedTracks(userId) })

  override fun getListeningStats(userId: UserId): DashboardStats {
    val since = Clock.System.now() - STATS_DAYS.days
    return DashboardStats.EMPTY.copy(listeningStats = runBlocking { buildListeningStats(userId, since) })
  }

  override fun getPlaylistCheckStats(): DashboardStats =
    DashboardStats.EMPTY.copy(playlistCheckStats = runBlocking { computePlaylistCheckStats() })

  override fun getCatalogStats(): DashboardStats =
    DashboardStats.EMPTY.copy(catalogStats = catalogBrowser.getCatalogStats())

  private fun computePlaybackStats(userId: UserId, since: Instant): DashboardStats {
    val total = appPlaybackRepository.countAll(userId)
    val last30Days = appPlaybackRepository.countSince(userId, since)
    val rawPerDay = appPlaybackRepository.countPerDaySince(userId, since)

    val countByDate = rawPerDay.toMap()
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val allDays = ((STATS_DAYS - 1) downTo 0).map { today - DatePeriod(days = it) }
    val maxCount = countByDate.values.maxOrNull() ?: 1L
    val perDay = allDays.map { date ->
      val count = countByDate[date] ?: 0L
      DayCount(
        date = date,
        count = count,
        heightPercent = if (maxCount > 0) (count * 100 / maxCount).toInt() else 0,
        dateLabel = "%02d.%02d".format(date.day, date.month.ordinal + 1),
      )
    }
    return DashboardStats.EMPTY.copy(
      totalPlaybackEvents = total,
      playbackEventsLast30Days = last30Days,
      playbackEventsPerDay = perDay,
    )
  }

  private fun computePlaylistMetadata(userId: UserId): DashboardStats {
    val playlists = playlistRepository.findByUserId(userId)
    val totalPlaylists = playlists.size.toLong()
    val syncedPlaylists = playlists.count { it.syncStatus == PlaylistSyncStatus.ACTIVE }.toLong()
    return DashboardStats.EMPTY.copy(
      syncedPlaylists = syncedPlaylists,
      totalPlaylists = totalPlaylists,
    )
  }

  private suspend fun computePlaylistCheckStats(): PlaylistCheckStats = coroutineScope {
    val totalChecksAsync = async(Dispatchers.IO) { playlistCheckRepository.countAll() }
    val succeededChecksAsync = async(Dispatchers.IO) { playlistCheckRepository.countSucceeded() }
    val totalChecks = totalChecksAsync.await()
    val succeededChecks = succeededChecksAsync.await()
    PlaylistCheckStats(
      succeededChecks = succeededChecks,
      totalChecks = totalChecks,
      allSucceeded = totalChecks == 0L || succeededChecks == totalChecks,
    )
  }

  private suspend fun buildRecentlyPlayedTracks(userId: UserId): List<RecentlyPlayedItem> {
    val recentPlaybackItems = appPlaybackRepository.findRecentlyPlayed(userId, recentlyPlayedLimit)
    val trackIds = recentPlaybackItems.map { it.trackId }.toSet()
    val trackMap = appTrackRepository.findByTrackIds(trackIds.map { TrackId(it) }.toSet()).associateBy { it.id.value }
    val albumIds = trackMap.values.mapNotNull { it.albumId }.toSet()
    val allArtistIds = trackMap.values.flatMap { it.allArtistIds() }.toSet()
    return coroutineScope {
      val albumMapAsync = async(Dispatchers.IO) { appAlbumRepository.findByAlbumIds(albumIds).associateBy { it.id.value } }
      val artistMapAsync = async(Dispatchers.IO) {
        appArtistRepository.findByArtistIds(allArtistIds.map { ArtistId(it) }.toSet()).associateBy { it.id.value }
      }
      val albumMap = albumMapAsync.await()
      val artistMap = artistMapAsync.await()
      recentPlaybackItems.map { playback ->
        val track = trackMap[playback.trackId]
        val trackArtistIds = track?.allArtistIds() ?: emptyList()
        val album = track?.albumId?.let { albumMap[it.value] }
        RecentlyPlayedItem(
          spotifyUserId = playback.userId,
          trackId = TrackId(playback.trackId),
          trackName = track?.title ?: playback.trackId,
          artistIds = trackArtistIds.map { ArtistId(it) },
          artistNames = trackArtistIds.mapNotNull { artistMap[it]?.artistName },
          playedAt = playback.playedAt,
          albumName = album?.title ?: track?.albumName,
          imageLink = album?.imageLink,
          durationSeconds = playback.secondsPlayed.takeIf { it > 0 },
        )
      }
    }
  }

  private suspend fun buildListeningStats(userId: UserId, since: Instant): ListeningStats {
    val secondsByTrackId = appPlaybackRepository.sumSecondsPlayedByTrackIdSince(userId, since)

    val allTrackIds = secondsByTrackId.keys.toSet()
    val statsTrackMap = appTrackRepository.findByTrackIds(allTrackIds.map { TrackId(it) }.toSet()).associateBy { it.id.value }

    val listenedMinutes = secondsByTrackId.values.sum() / SECONDS_PER_MINUTE
    val statsAlbumIds = statsTrackMap.values.mapNotNull { it.albumId }.toSet()
    val statsArtistIds = statsTrackMap.values.flatMap { it.allArtistIds() }.toSet()
    return coroutineScope {
      val statsAlbumMapAsync = async(Dispatchers.IO) { appAlbumRepository.findByAlbumIds(statsAlbumIds).associateBy { it.id.value } }
      val statsArtistMapAsync = async(Dispatchers.IO) {
        appArtistRepository.findByArtistIds(statsArtistIds.map { ArtistId(it) }.toSet()).associateBy { it.id.value }
      }
      val statsAlbumMap = statsAlbumMapAsync.await()
      val statsArtistMap = statsArtistMapAsync.await()

      val topTracks = buildTopEntries(secondsByTrackId, { statsTrackMap[it]?.title ?: it }) { id ->
        statsTrackMap[id]?.albumId?.let { statsAlbumMap[it.value]?.imageLink }
      }

      val secondsByArtistId = mutableMapOf<String, Long>()
      for ((trackId, seconds) in secondsByTrackId) {
        val track = statsTrackMap[trackId] ?: continue
        for (artistId in track.allArtistIds()) {
          secondsByArtistId.merge(artistId, seconds, Long::plus)
        }
      }
      val topArtists = buildTopEntries(secondsByArtistId, { statsArtistMap[it]?.artistName ?: it }) { id ->
        statsArtistMap[id]?.imageLink
      }

      ListeningStats(
        listenedMinutesLast30Days = listenedMinutes,
        topTracksLast30Days = topTracks,
        topArtistsLast30Days = topArtists,
      )
    }
  }

  private fun buildTopEntries(
    secondsById: Map<String, Long>,
    nameResolver: (String) -> String,
    imageResolver: ((String) -> String?)? = null,
  ): List<TopEntry> =
    secondsById.entries
      .sortedByDescending { it.value }
      .take(topEntriesLimit)
      .map { (id, seconds) -> TopEntry(name = nameResolver(id), totalMinutes = seconds / SECONDS_PER_MINUTE, imageLink = imageResolver?.invoke(id)) }

  companion object {
    private const val STATS_DAYS = 30
    private const val SECONDS_PER_MINUTE = 60L
  }

  private fun AppTrack.allArtistIds(): List<String> = listOf(artistId.value) + additionalArtistIds.map { it.value }
}
