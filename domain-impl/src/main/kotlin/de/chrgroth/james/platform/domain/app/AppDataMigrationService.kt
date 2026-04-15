package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.port.`in`.app.AppDataMigrationPort
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class AppDataMigrationService(
  private val appRepository: AppRepositoryPort,
) : AppDataMigrationPort {

  override fun deleteAppsWithoutDeveloperId() {
    logger.info { "Deleting apps without developer id" }
    appRepository.deleteAllWithoutDeveloperId()
    logger.info { "Deletion of apps without developer id completed" }
  }

  companion object : KLogging()
}
