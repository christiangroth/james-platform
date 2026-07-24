package de.chrgroth.james.platform.adapter.`in`.scheduler

import de.chrgroth.james.platform.domain.port.`in`.imports.ImportCleanupPort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class ImportDocumentCleanupJob(
  private val importCleanup: ImportCleanupPort,
) {

  @Scheduled(cron = "{app.imports.cleanup.cron}")
  fun cleanupStaleImportDocuments() {
    importCleanup.cleanupStaleImportDocuments()
  }
}
