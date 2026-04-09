package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistId
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class AppPlaylistCheckRepositoryAdapter(
  private val appPlaylistCheckDocumentRepository: AppPlaylistCheckDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : AppPlaylistCheckRepositoryPort {

  override fun save(check: AppPlaylistCheck) {
    logger.info { "Saving playlist check ${check.checkId} for playlist ${check.playlistId}" }
    val document = check.toDocument()
    mongoQueryMetrics.timed("app_playlist_check.save") {
      appPlaylistCheckDocumentRepository.persistOrUpdate(document)
    }
  }

  override fun findByCheckId(checkId: String): AppPlaylistCheck? =
    mongoQueryMetrics.timed("app_playlist_check.findByCheckId") {
      appPlaylistCheckDocumentRepository.findById(checkId)?.toDomain()
    }

  override fun findAll(): List<AppPlaylistCheck> =
    mongoQueryMetrics.timed("app_playlist_check.findAll") {
      appPlaylistCheckDocumentRepository.listAll().map { it.toDomain() }
    }

  override fun countAll(): Long =
    mongoQueryMetrics.timed("app_playlist_check.countAll") {
      appPlaylistCheckDocumentRepository.count()
    }

  override fun countSucceeded(): Long =
    mongoQueryMetrics.timed("app_playlist_check.countSucceeded") {
      appPlaylistCheckDocumentRepository.count("succeeded = ?1", true)
    }

  override fun deleteByPlaylistId(playlistId: String) {
    logger.info { "Deleting playlist check documents for playlist $playlistId" }
    mongoQueryMetrics.timed("app_playlist_check.deleteByPlaylistId") {
      appPlaylistCheckDocumentRepository.delete("playlistId = ?1", playlistId)
    }
  }

  override fun deleteAll() {
    logger.info { "Deleting all playlist check documents" }
    mongoQueryMetrics.timed("app_playlist_check.deleteAll") {
      appPlaylistCheckDocumentRepository.deleteAll()
    }
  }

  private fun AppPlaylistCheckDocument.toDomain() = AppPlaylistCheck(
    checkId = checkId,
    playlistId = PlaylistId(playlistId),
    lastCheck = lastCheck.toKotlinInstant(),
    succeeded = succeeded,
    violations = violations,
  )

  private fun AppPlaylistCheck.toDocument() = AppPlaylistCheckDocument().apply {
    checkId = this@toDocument.checkId
    playlistId = this@toDocument.playlistId.value
    lastCheck = this@toDocument.lastCheck.toJavaInstant()
    succeeded = this@toDocument.succeeded
    violations = this@toDocument.violations
  }

  companion object : KLogging()
}
