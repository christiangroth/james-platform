package de.chrgroth.spotify.control.domain.playlist

/*
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistPort
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.catalog.SyncController
import de.chrgroth.spotify.control.domain.catalog.CatalogSyncRequest
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import kotlin.time.Clock

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class PlaylistService(
  private val userRepository: UserRepositoryPort,
  private val playlistRepository: PlaylistRepositoryPort,
  private val spotifyAccessToken: SpotifyAccessTokenPort,
  private val spotifyPlaylist: SpotifyPlaylistPort,
  private val outboxPort: OutboxPort,
  private val dashboardRefresh: DashboardRefreshPort,
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort,
  private val syncController: SyncController,
) : PlaylistPort {

  override fun getPlaylists(userId: UserId): List<PlaylistInfo> = playlistRepository.findByUserId(userId)

  override fun getTrackCounts(userId: UserId): Map<String, Int> = playlistRepository.findTrackCountsByUserId(userId)

  override fun enqueueUpdates() {
    val users = userRepository.findAll()
    logger.info { "Scheduling playlist sync for ${users.size} user(s)" }
    users.forEach { user ->
      outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistInfo(user.spotifyUserId))
    }
  }

  override fun syncPlaylists(userId: UserId): Either<DomainError, Unit> {
    userRepository.findById(userId) ?: run {
      logger.warn { "User not found for playlist sync: ${userId.value}" }
      return Unit.right()
    }
    val accessToken = spotifyAccessToken.getValidAccessToken(userId)
    return spotifyPlaylist.getPlaylists(userId, accessToken).map { spotifyPlaylists ->
      val now = Clock.System.now()
      val existingById = playlistRepository.findByUserId(userId).associateBy { it.spotifyPlaylistId }
      val updatedPlaylists = spotifyPlaylists.filter { it.ownerId == userId.value }.map { item ->
        val existing = existingById[item.id]
        PlaylistInfo(
          spotifyPlaylistId = item.id,
          snapshotId = item.snapshotId,
          lastSnapshotIdSyncTime = if (existing == null || existing.snapshotId != item.snapshotId) now else existing.lastSnapshotIdSyncTime,
          name = item.name,
          syncStatus = existing?.syncStatus ?: PlaylistSyncStatus.PASSIVE,
          type = existing?.type,
        )
      }
      logger.info { "Synced ${updatedPlaylists.size} playlist(s) for user ${userId.value}" }
      playlistRepository.replaceAll(userId, updatedPlaylists)
      if (updatedPlaylists.size != existingById.size) {
        dashboardRefresh.notifyUserPlaylistMetadata(userId)
      }
      updatedPlaylists
        .filter { it.syncStatus == PlaylistSyncStatus.ACTIVE }
        .filter { playlist ->
          val existing = existingById[playlist.spotifyPlaylistId]
          existing == null ||
            existing.snapshotId != playlist.snapshotId ||
            playlistRepository.findByUserIdAndPlaylistId(userId, playlist.spotifyPlaylistId) == null
        }
        .forEach { playlist ->
          logger.info { "Enqueueing SyncPlaylistData for active playlist ${playlist.spotifyPlaylistId} (user ${userId.value})" }
          outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(userId, playlist.spotifyPlaylistId))
        }
    }
  }

  override fun syncPlaylistData(userId: UserId, playlistId: String, nextUrl: String?, snapshotId: String?): Either<DomainError, Unit> {
    userRepository.findById(userId) ?: run {
      logger.warn { "User not found for playlist data sync: ${userId.value}" }
      return Unit.right()
    }
    val accessToken = spotifyAccessToken.getValidAccessToken(userId)
    val isFirstPage = nextUrl == null
    return spotifyPlaylist.getPlaylistTracksPage(userId, accessToken, playlistId, nextUrl).map { page ->
      logger.info { "Synced page of ${page.tracks.size} track(s) for playlist $playlistId (user ${userId.value})" }
      if (snapshotId != null && page.snapshotId != snapshotId) {
        logger.warn { "Snapshot changed for playlist $playlistId (expected $snapshotId, got ${page.snapshotId}), restarting sync from first page" }
        outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(userId, playlistId))
        return@map
      }
      if (isFirstPage) {
        playlistRepository.save(userId, Playlist(playlistId, page.tracks))
      } else {
        playlistRepository.appendTracks(userId, playlistId, page.tracks)
      }

      val catalogRequests = page.tracks.map { CatalogSyncRequest(it.trackId.value, it.artistIds.map { a -> a.value }) }
      syncController.syncForTracks(catalogRequests, userId)

      if (page.nextUrl != null) {
        logger.info { "Enqueueing next SyncPlaylistData page for playlist $playlistId (user ${userId.value})" }
        outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(userId, playlistId, page.nextUrl, page.snapshotId))
      } else {
        logger.info { "Completed all pages for playlist $playlistId (user ${userId.value})" }
        playlistRepository.updateLastSyncTime(userId, playlistId, Clock.System.now())
        outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks(userId, playlistId))
      }
    }
  }

  override fun updateSyncStatus(userId: UserId, playlistId: String, syncStatus: PlaylistSyncStatus): Either<DomainError, Unit> {
    userRepository.findById(userId) ?: run {
      logger.warn { "User not found for playlist sync status update: ${userId.value}" }
      return PlaylistSyncError.PLAYLIST_NOT_FOUND.left()
    }
    val playlists = playlistRepository.findByUserId(userId)
    val playlist = playlists.find { it.spotifyPlaylistId == playlistId } ?: run {
      logger.warn { "Playlist $playlistId not found for user ${userId.value}" }
      return PlaylistSyncError.PLAYLIST_NOT_FOUND.left()
    }
    val updatedPlaylists = playlists.map {
      if (it.spotifyPlaylistId == playlistId) {
        val newType = when {
          syncStatus == PlaylistSyncStatus.PASSIVE -> null
          it.type != null -> it.type
          it.name.equals("all", ignoreCase = true) -> PlaylistType.ALL
          it.name.matches(YEAR_NAME_REGEX) -> PlaylistType.YEAR
          it.name.equals(PlaylistType.SINGULARITY_PLAYLIST_NAME, ignoreCase = true) -> PlaylistType.SINGULARITY
          else -> PlaylistType.UNKNOWN
        }
        it.copy(syncStatus = syncStatus, type = newType)
      } else {
        it
      }
    }
    logger.info { "Updated sync status for playlist $playlistId (user ${userId.value}) to $syncStatus" }
    playlistRepository.replaceAll(userId, updatedPlaylists)
    dashboardRefresh.notifyUserPlaylistMetadata(userId)
    if (syncStatus == PlaylistSyncStatus.PASSIVE) {
      logger.info { "Deleting checks for deactivated playlist $playlistId (user ${userId.value})" }
      playlistCheckRepository.deleteByPlaylistId(playlistId)
    } else if (syncStatus == PlaylistSyncStatus.ACTIVE) {
      logger.info { "Enqueueing SyncPlaylistData for activated playlist $playlistId (user ${userId.value})" }
      outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(userId, playlistId))
    }
    return Unit.right()
  }

  override fun updatePlaylistType(userId: UserId, playlistId: String, type: PlaylistType): Either<DomainError, Unit> {
    val validationError = validatePlaylistTypeUpdate(userId, playlistId, type)
    if (validationError != null) return validationError.left()
    val playlists = playlistRepository.findByUserId(userId)
    val updatedPlaylists = playlists.map {
      if (it.spotifyPlaylistId == playlistId) it.copy(type = type) else it
    }
    logger.info { "Updated type for playlist $playlistId (user ${userId.value}) to $type" }
    playlistRepository.replaceAll(userId, updatedPlaylists)
    dashboardRefresh.notifyUserPlaylistMetadata(userId)
    return Unit.right()
  }

  private fun validatePlaylistTypeUpdate(userId: UserId, playlistId: String, type: PlaylistType): PlaylistSyncError? {
    userRepository.findById(userId) ?: run {
      logger.warn { "User not found for playlist type update: ${userId.value}" }
      return PlaylistSyncError.PLAYLIST_NOT_FOUND
    }
    val playlists = playlistRepository.findByUserId(userId)
    val playlist = playlists.find { it.spotifyPlaylistId == playlistId }
    return when {
      playlist == null -> {
        logger.warn { "Playlist $playlistId not found for user ${userId.value}" }
        PlaylistSyncError.PLAYLIST_NOT_FOUND
      }
      playlist.syncStatus != PlaylistSyncStatus.ACTIVE -> {
        logger.warn { "Playlist $playlistId is not active for user ${userId.value}, cannot set type" }
        PlaylistSyncError.PLAYLIST_NOT_ACTIVE
      }
      type == PlaylistType.ALL && playlists.any { it.type == PlaylistType.ALL && it.spotifyPlaylistId != playlistId } -> {
        logger.warn { "Playlist type ALL already assigned to another playlist for user ${userId.value}" }
        PlaylistSyncError.PLAYLIST_TYPE_CONFLICT
      }
      type == PlaylistType.SINGULARITY && playlists.any { it.type == PlaylistType.SINGULARITY && it.spotifyPlaylistId != playlistId } -> {
        logger.warn { "Playlist type SINGULARITY already assigned to another playlist for user ${userId.value}" }
        PlaylistSyncError.PLAYLIST_TYPE_CONFLICT
      }
      else -> null
    }
  }

  override fun enqueueSyncPlaylistData(userId: UserId, playlistId: String): Either<DomainError, Unit> {
    userRepository.findById(userId) ?: run {
      logger.warn { "User not found for playlist data sync enqueue: ${userId.value}" }
      return PlaylistSyncError.PLAYLIST_NOT_FOUND.left()
    }
    val playlists = playlistRepository.findByUserId(userId)
    val playlist = playlists.find { it.spotifyPlaylistId == playlistId } ?: run {
      logger.warn { "Playlist $playlistId not found for user ${userId.value}" }
      return PlaylistSyncError.PLAYLIST_NOT_FOUND.left()
    }
    return if (playlist.syncStatus != PlaylistSyncStatus.ACTIVE) {
      logger.warn { "Playlist $playlistId is not active for user ${userId.value}, skipping sync enqueue" }
      PlaylistSyncError.PLAYLIST_SYNC_INACTIVE.left()
    } else {
      logger.info { "Enqueueing SyncPlaylistData for playlist $playlistId (user ${userId.value})" }
      outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(userId, playlistId))
      Unit.right()
    }
  }

  override fun handle(event: DomainOutboxEvent.SyncPlaylistInfo): Either<DomainError, Unit> =
    syncPlaylists(event.userId)

  override fun handle(event: DomainOutboxEvent.SyncPlaylistData): Either<DomainError, Unit> =
    syncPlaylistData(event.userId, event.playlistId, event.nextUrl, event.snapshotId)

  companion object : KLogging() {
    private val YEAR_NAME_REGEX = Regex("\\d{4}")
  }
}
*/
