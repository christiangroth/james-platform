package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.TimeGranularity
import de.chrgroth.james.platform.domain.model.app.decodeListValue
import de.chrgroth.james.platform.domain.model.app.decodeObjectValue
import de.chrgroth.james.platform.domain.model.app.encodeListValue
import de.chrgroth.james.platform.domain.model.app.encodeObjectValue
import de.chrgroth.james.platform.domain.port.`in`.app.AppDataMigrationPort
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.DurationPropertyLocation
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class AppDataMigrationService(
  private val appRepository: AppRepositoryPort,
  private val appVersionRepository: AppVersionRepositoryPort,
  private val appDataRepository: AppDataRepositoryPort,
  @param:ConfigProperty(name = "quarkus.application.version")
  private val appBuildVersion: String,
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

  override fun migrateDurationProperties() {
    logger.info { "Migrating DURATION properties to Property Units" }
    val locations = appVersionRepository.migrateDurationProperties(DURATION_MIGRATION_DEFAULT_GRANULARITY)
    if (locations.isEmpty()) {
      logger.info { "No DURATION properties found to migrate" }
      return
    }

    val locationsByEntity = locations.groupBy { it.entityDefinitionId }
    var convertedCount = 0
    // findAll is acceptable here: migration runs once at startup before serving requests
    appDataRepository.findAll().forEach { appData ->
      val entityLocations = locationsByEntity[appData.entityType] ?: return@forEach
      val migratedData = migrateDurationValues(appData.data, entityLocations)
      if (migratedData != appData.data) {
        appDataRepository.save(appData.copy(data = migratedData))
        convertedCount++
      }
    }
    logger.info {
      "Migrated ${locations.size} DURATION propert${if (locations.size == 1) "y" else "ies"} across " +
        "${locationsByEntity.size} entity definition(s), converted $convertedCount app data object(s)"
    }
  }

  override fun backfillAppBuildVersion() {
    logger.info { "Backfilling app build version for existing app data" }
    appDataRepository.backfillAppBuildVersion(appBuildVersion)
    logger.info { "Backfilled app build version $appBuildVersion for existing app data" }
  }

  /** Converts every value at [locations] within [data] from legacy duration text to seconds. Values that no longer parse are left untouched. */
  private fun migrateDurationValues(data: Map<String, String?>, locations: List<DurationPropertyLocation>): Map<String, String?> {
    val locationsByTopLevelId = locations.groupBy { it.path.first().value }
    val result = data.toMutableMap()
    for ((topLevelId, topLevelLocations) in locationsByTopLevelId) {
      val rawValue = result[topLevelId] ?: continue
      val directLocation = topLevelLocations.singleOrNull { it.path.size == 1 }
      result[topLevelId] = if (directLocation != null) {
        convertDurationLeaf(rawValue, directLocation.isListItem)
      } else {
        val nested = decodeObjectValue(rawValue).toMutableMap()
        topLevelLocations.forEach { migrateNestedDurationValue(nested, it.path.drop(1), it.isListItem) }
        encodeObjectValue(nested)
      }
    }
    return result
  }

  @Suppress("UNCHECKED_CAST")
  private fun migrateNestedDurationValue(container: MutableMap<String, Any?>, remainingPath: List<PropertyId>, isListItem: Boolean) {
    val key = remainingPath.first().value
    if (remainingPath.size == 1) {
      val raw = container[key] as? String ?: return
      container[key] = convertDurationLeaf(raw, isListItem)
      return
    }
    val nested = (container[key] as? Map<String, Any?>)?.toMutableMap() ?: return
    migrateNestedDurationValue(nested, remainingPath.drop(1), isListItem)
    container[key] = nested
  }

  private fun convertDurationLeaf(raw: String, isListItem: Boolean): String =
    if (isListItem) {
      encodeListValue(decodeListValue(raw).map { convertDurationText(it) })
    } else {
      convertDurationText(raw)
    }

  private fun convertDurationText(raw: String): String {
    if (raw.isBlank()) return raw
    val seconds = parseLegacyDurationSeconds(raw) ?: return raw
    return seconds.toString()
  }

  /**
   * Reimplements the format previously accepted by the removed `DurationFormat.kt` (colon-separated `[[hh:]mm:]ss`, or unit-suffixed
   * `1d 2h 30m 15s`), kept private to this one-time migration since the public parsing utility no longer exists.
   */
  private fun parseLegacyDurationSeconds(raw: String): Long? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    if (LEGACY_DURATION_COLON_FORMAT.matches(trimmed)) {
      val numbers = trimmed.split(":").map { it.toLong() }
      val (hours, minutes, seconds) = List(3 - numbers.size) { 0L } + numbers
      return hours * 3600 + minutes * 60 + seconds
    }

    val match = LEGACY_DURATION_UNIT_SUFFIX_FORMAT.matchEntire(trimmed) ?: return null
    val (days, hours, minutes, seconds) = match.destructured
    if (days.isEmpty() && hours.isEmpty() && minutes.isEmpty() && seconds.isEmpty()) return null
    fun String.partOrZero() = if (isEmpty()) 0L else toLong()
    return days.partOrZero() * 86400 + hours.partOrZero() * 3600 + minutes.partOrZero() * 60 + seconds.partOrZero()
  }

  companion object : KLogging() {
    const val MISSING_RELEASE_NOTES = "missing"
    const val FALLBACK_DISPLAY_TEXT = "Display Text"
    val DURATION_MIGRATION_DEFAULT_GRANULARITY: TimeGranularity = TimeGranularity.MINUTES
    private val LEGACY_DURATION_COLON_FORMAT = Regex("""^\d+(:\d+){0,2}$""")
    private val LEGACY_DURATION_UNIT_SUFFIX_FORMAT = Regex("""^(?:(\d+)d\s*)?(?:(\d+)h\s*)?(?:(\d+)m\s*)?(?:(\d+)s\s*)?$""")
  }
}
