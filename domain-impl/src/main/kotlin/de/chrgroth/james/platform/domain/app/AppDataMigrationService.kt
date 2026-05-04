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

  override fun renameCollections() {
    logger.info { "Renaming app collections" }
    appVersionRepository.renameToNewCollection()
    appRepository.renameToNewCollection()
    logger.info { "Renaming app collections completed" }
  }

  override fun addMissingReleaseNotes() {
    logger.info { "Adding missing release notes to published versions" }
    val versionsToUpdate = appVersionRepository.findAllPublishedWithoutReleaseNotes()
    versionsToUpdate.forEach { version ->
      appVersionRepository.save(version.copy(releaseNotes = MISSING_RELEASE_NOTES))
    }
    logger.info { "Added missing release notes to ${versionsToUpdate.size} published version(s)" }
  }

  override fun backfillEntityDisplayText() {
    logger.info { "Backfilling entity display text for existing versions" }
    var count = 0
    // findAll is acceptable here: migration runs once at startup before serving requests
    appVersionRepository.findAll().forEach { version ->
      val nullCount = version.entityDefinitions.count { it.displayText == null }
      if (nullCount > 0) {
        val updated = version.copy(
          entityDefinitions = version.entityDefinitions.map { entity ->
            if (entity.displayText == null) entity.copy(displayText = FALLBACK_DISPLAY_TEXT) else entity
          },
        )
        appVersionRepository.save(updated)
        count += nullCount
      }
    }
    logger.info { "Backfilled display text for $count entity definition(s)" }
  }

  companion object : KLogging() {
    const val MISSING_RELEASE_NOTES = "missing"
    const val FALLBACK_DISPLAY_TEXT = "Display Text"
  }
}
