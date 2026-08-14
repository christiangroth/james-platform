package de.chrgroth.james.platform.adapter.`in`.scheduler

import de.chrgroth.james.platform.domain.port.`in`.imports.ImportCleanupPort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class ImportJobCleanupJob(
  private val importCleanup: ImportCleanupPort,
) {

  @Scheduled(cron = "{app.imports.cleanup.cron}")
  fun cleanupStaleImportJobs() {
    importCleanup.cleanupStaleImportJobs()
  }
}
