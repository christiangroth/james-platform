package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.port.`in`.imports.ImportCleanupPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportJobRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Instant
import java.time.temporal.ChronoUnit

@ApplicationScoped
@Suppress("Unused")
class ImportCleanupService(
  private val importJobRepository: ImportJobRepositoryPort,
  private val importCleanupMetrics: ImportCleanupMetrics,
  @param:ConfigProperty(name = "app.imports.cleanup.retention-days")
  private val retentionDays: Long,
) : ImportCleanupPort {

  override fun cleanupStaleImportJobs(): Int {
    val startMs = System.currentTimeMillis()
    return try {
      val cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS)
      val deleted = importJobRepository.deleteAllLastChangedBefore(cutoff)
      val durationMs = System.currentTimeMillis() - startMs
      importCleanupMetrics.record(durationMs, deleted, success = true)
      if (deleted > 0) {
        logger.info { "Import job cleanup deleted $deleted stale job(s) older than $retentionDays days" }
      }
      deleted.toInt()
    } catch (e: Exception) {
      val durationMs = System.currentTimeMillis() - startMs
      importCleanupMetrics.record(durationMs, 0, success = false)
      logger.error(e) { "Import job cleanup failed" }
      throw e
    }
  }

  companion object : KLogging()
}
