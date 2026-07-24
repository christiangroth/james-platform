package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.port.`in`.imports.ImportCleanupPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportDocumentRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Instant
import java.time.temporal.ChronoUnit

@ApplicationScoped
@Suppress("Unused")
class ImportCleanupService(
  private val importDocumentRepository: ImportDocumentRepositoryPort,
  private val importCleanupMetrics: ImportCleanupMetrics,
  @param:ConfigProperty(name = "app.imports.cleanup.retention-days")
  private val retentionDays: Long,
) : ImportCleanupPort {

  override fun cleanupStaleImportDocuments(): Int {
    val startMs = System.currentTimeMillis()
    return try {
      val cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS)
      val deleted = importDocumentRepository.deleteAllLastChangedBefore(cutoff)
      val durationMs = System.currentTimeMillis() - startMs
      importCleanupMetrics.record(durationMs, deleted, success = true)
      if (deleted > 0) {
        logger.info { "Import document cleanup deleted $deleted stale document(s) older than $retentionDays days" }
      }
      deleted.toInt()
    } catch (e: Exception) {
      val durationMs = System.currentTimeMillis() - startMs
      importCleanupMetrics.record(durationMs, 0, success = false)
      logger.error(e) { "Import document cleanup failed" }
      throw e
    }
  }

  companion object : KLogging()
}
