package de.chrgroth.james.platform.domain.catalog

import de.chrgroth.james.platform.domain.model.catalog.ArtistId
import de.chrgroth.james.platform.domain.model.catalog.TrackId
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import de.chrgroth.james.platform.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.james.platform.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.james.platform.domain.port.out.infra.OutboxPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
class SyncController(
  private val appTrackRepository: AppTrackRepositoryPort,
  private val appArtistRepository: AppArtistRepositoryPort,
  private val outboxPort: OutboxPort,
) {
  /**
   * For each track in the list:
   * - Checks which tracks are missing from app_track.
   * - For missing tracks: enqueues SyncArtistDetails for any artists not yet in the catalog.
   */
  fun syncForTracks(tracks: List<CatalogSyncRequest>, userId: UserId) {
    if (tracks.isEmpty()) return
    val trackIds = tracks.map { TrackId(it.trackId) }.toSet()
    val existingTrackIds = appTrackRepository.findByTrackIds(trackIds).map { it.id }.toSet()
    val missingTracks = tracks.filter { TrackId(it.trackId) !in existingTrackIds }
    if (missingTracks.isEmpty()) return
    logger.info { "Found ${missingTracks.size} missing track(s) in catalog, triggering sync" }
    val artistIds = missingTracks.flatMap { it.artistIds }.distinct()
    syncArtists(artistIds, userId)
  }

  /**
   * Checks which artists are missing from app_artist and enqueues SyncArtistDetails.
   */
  fun syncArtists(artistIds: List<String>, userId: UserId) {
    if (artistIds.isEmpty()) return
    val existingArtistIds = appArtistRepository.findByArtistIds(artistIds.map { ArtistId(it) }.toSet()).map { it.id.value }.toSet()
    val newArtistIds = artistIds.filter { it !in existingArtistIds }.distinct()
    if (newArtistIds.isNotEmpty()) {
      logger.info { "Enqueueing SyncArtistDetails for ${newArtistIds.size} new artist(s)" }
      newArtistIds.forEach { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails(it, userId)) }
    }
  }

  companion object : KLogging()
}
