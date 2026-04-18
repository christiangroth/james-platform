package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.port.`in`.app.AppDataMigrationPort
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class AppDataMigrationService(
  private val appRepository: AppRepositoryPort,
  private val appVersionRepository: AppVersionRepositoryPort,
) : AppDataMigrationPort {

  override fun deleteAppsWithoutDeveloperId() {
    logger.info { "Deleting apps without developer id" }
    appRepository.deleteAllWithoutDeveloperId()
    logger.info { "Deletion of apps without developer id completed" }
  }

  override fun deleteAllApps() {
    logger.info { "Deleting all app versions" }
    appVersionRepository.deleteAll()
    logger.info { "Deleting all apps" }
    appRepository.deleteAll()
    logger.info { "Deletion of all apps and versions completed" }
  }

  companion object : KLogging()
}
