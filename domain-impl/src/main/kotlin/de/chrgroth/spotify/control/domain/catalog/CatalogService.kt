package de.chrgroth.spotify.control.domain.catalog

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.ArtistSettingsError
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogPort
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
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class CatalogService(
  private val spotifyAccessToken: SpotifyAccessTokenPort,
  private val spotifyCatalog: SpotifyCatalogPort,
  private val appArtistRepository: AppArtistRepositoryPort,
  private val appTrackRepository: AppTrackRepositoryPort,
  private val appAlbumRepository: AppAlbumRepositoryPort,
  private val userRepository: UserRepositoryPort,
  private val outboxPort: OutboxPort,
  private val playlistRepository: PlaylistRepositoryPort,
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort,
  private val dashboardRefresh: DashboardRefreshPort,
  private val syncController: SyncController,
) : CatalogPort {

  // --- Artist Settings ---

  override fun findAllArtists(): List<AppArtist> = appArtistRepository.findAll()

  // --- Catalog Sync ---

  override fun syncArtistDetails(artistId: String, userId: UserId): Either<DomainError, Unit> {
    val existing = appArtistRepository.findByArtistIds(setOf(ArtistId(artistId))).firstOrNull()
    if (existing != null) {
      logger.debug { "Artist $artistId already synced, skipping" }
      return Unit.right()
    }
    logger.info { "Fetching details for artist $artistId (user ${userId.value})" }
    val accessToken = spotifyAccessToken.getValidAccessToken(userId)
    return spotifyCatalog.getArtist(userId, accessToken, artistId)
      .flatMap { detail ->
        if (detail != null) {
          appArtistRepository.upsertAll(listOf(detail))
          logger.info { "Updated sync data for artist $artistId, enqueueing album sync" }
          outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums(artistId, userId))
          dashboardRefresh.notifyCatalogData()
        } else {
          logger.warn { "No data returned from Spotify for artist $artistId" }
        }
        Unit.right()
      }
  }

  override fun resyncCatalog(): Either<DomainError, Unit> {
    val allArtistIds = appArtistRepository.findAll().map { it.id.value }
    val userId = userRepository.findAll().firstOrNull()?.spotifyUserId
    logger.info { "Re-syncing catalog: ${allArtistIds.size} artist(s)" }
    if (userId != null) {
      allArtistIds.forEach { outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums(it, userId)) }
    }
    return Unit.right()
  }

  override fun resyncArtist(artistId: String): Either<DomainError, Unit> {
    appArtistRepository.findByArtistIds(setOf(ArtistId(artistId))).firstOrNull()
      ?: return ArtistSettingsError.ARTIST_NOT_FOUND.left()
    val userId = userRepository.findAll().firstOrNull()?.spotifyUserId ?: run {
      logger.warn { "No users available for artist resync, skipping $artistId" }
      return Unit.right()
    }
    logger.info { "Re-syncing artist $artistId and all their albums" }
    outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums(artistId, userId))
    return Unit.right()
  }

  override fun wipeCatalog(): Either<DomainError, Unit> {
    logger.info { "Wiping all catalog data" }
    appArtistRepository.deleteAll()
    appAlbumRepository.deleteAll()
    appTrackRepository.deleteAll()
    playlistRepository.setAllSyncInactive()
    playlistCheckRepository.deleteAll()
    logger.info { "Catalog wipe complete" }
    return Unit.right()
  }

  private fun syncAlbumDetails(albumId: String): Either<DomainError, Int> {
    val userId = userRepository.findAll().firstOrNull()?.spotifyUserId
    if (userId == null) {
      logger.debug { "No users available, skipping syncAlbumDetails" }
      return 0.right()
    }
    logger.info { "Syncing album $albumId" }
    val accessToken = spotifyAccessToken.getValidAccessToken(userId)
    val result = spotifyCatalog.getAlbum(userId, accessToken, albumId)
    return when (result) {
      is Either.Left -> result.value.left()
      is Either.Right -> {
        val albumResult = result.value
        if (albumResult.tracks.isNotEmpty()) {
          appTrackRepository.upsertAll(albumResult.tracks)
          appAlbumRepository.upsertAll(listOf(albumResult.album))
          val artistIds = albumResult.tracks
            .flatMap { t -> (listOf(t.artistId) + t.additionalArtistIds).map { it.value } }
            .filter { it.isNotBlank() }.distinct()
          syncController.syncArtists(artistIds, userId)
          val expectedTracks = albumResult.album.totalTracks
          if (expectedTracks != null && albumResult.tracks.size < expectedTracks) {
            logger.warn { "Album $albumId: synced ${albumResult.tracks.size} track(s) but album reports $expectedTracks total" }
          }
          dashboardRefresh.notifyCatalogData()
        }
        logger.info { "Synced album $albumId: ${albumResult.tracks.size} track(s)" }
        1.right()
      }
    }
  }

  // --- Outbox Handlers ---

  override fun handle(event: DomainOutboxEvent.SyncArtistDetails): Either<DomainError, Unit> =
    syncArtistDetails(event.artistId, event.userId)

  override fun handle(event: DomainOutboxEvent.SyncArtistAlbums): Either<DomainError, Unit> =
    syncArtistAlbums(event.artistId, event.userId)

  override fun handle(event: DomainOutboxEvent.SyncAlbumDetails): Either<DomainError, Unit> =
    syncAlbumDetails(event.albumId).map { Unit }

  override fun handle(event: DomainOutboxEvent.ResyncCatalog): Either<DomainError, Unit> =
    resyncCatalog()

  override fun enqueueArtistAlbumsSync(partition: Int, totalPartitions: Int) {
    val allArtists = appArtistRepository.findAll()
    val userId = userRepository.findAll().firstOrNull()?.spotifyUserId
    if (userId == null) {
      logger.warn { "No users available for artist albums sync, skipping partition $partition/$totalPartitions" }
      return
    }
    val partitioned = allArtists.filterIndexed { idx, _ -> idx % totalPartitions == partition }
    logger.info { "Enqueueing artist album syncs for ${partitioned.size} artist(s) (partition $partition/$totalPartitions)" }
    partitioned.forEach { artist ->
      outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums(artist.id.value, userId))
    }
  }

  private fun syncArtistAlbums(artistId: String, userId: UserId): Either<DomainError, Unit> {
    logger.info { "Syncing all album ids for artist $artistId (user ${userId.value})" }
    val accessToken = spotifyAccessToken.getValidAccessToken(userId)
    return spotifyCatalog.getArtistAlbumIds(userId, accessToken, artistId)
      .flatMap { albumIds ->
        val existingAlbumIds = appAlbumRepository.findByAlbumIds(albumIds.map { AlbumId(it) }.toSet()).map { it.id.value }.toSet()
        val newAlbumIds = albumIds.filter { it !in existingAlbumIds }
        if (newAlbumIds.isNotEmpty()) {
          logger.info { "Enqueueing SyncAlbumDetails for ${newAlbumIds.size} new album(s) of artist $artistId" }
          newAlbumIds.forEach { outboxPort.enqueue(DomainOutboxEvent.SyncAlbumDetails(it)) }
        } else {
          logger.debug { "All ${albumIds.size} album(s) for artist $artistId already in catalog" }
        }
        Unit.right()
      }
  }

  companion object : KLogging()
}
