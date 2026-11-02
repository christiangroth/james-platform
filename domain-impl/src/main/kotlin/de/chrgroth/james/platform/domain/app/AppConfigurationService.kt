package de.chrgroth.james.platform.domain.app

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.AppDataError
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.encodeObjectValue
import de.chrgroth.james.platform.domain.port.`in`.app.AppConfigurationPort
import de.chrgroth.james.platform.domain.port.`in`.app.SmartDefaultPort
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.Instant
import java.util.UUID
import kotlin.time.Clock

/**
 * Creates and reads the configuration object of an installation (ADR 0027). The object is built from the property defaults (top-level
 * properties may also use their smart default) - publish validation guarantees every required property has one, so it is valid on creation.
 * Creation is idempotent: install, upgrade and the first access of the settings page all call [ensureConfiguration].
 */
@ApplicationScoped
@Suppress("Unused")
class AppConfigurationService(
  private val installedAppRepository: InstalledAppRepositoryPort,
  private val appVersionRepository: AppVersionRepositoryPort,
  private val appDataRepository: AppDataRepositoryPort,
  private val smartDefault: SmartDefaultPort,
) : AppConfigurationPort {

  override fun ensureConfiguration(installedAppId: InstalledAppId) {
    val installedApp = installedAppRepository.findById(installedAppId) ?: return
    val version = resolveInstalledAppVersion(installedApp, appVersionRepository) ?: return
    val entity = version.configurationEntity ?: return
    findOrCreate(installedApp, entity)
  }

  override fun getConfiguration(userId: String, installedAppId: String): Either<DomainError, AppData> {
    val installedApp = installedAppRepository.findById(InstalledAppId(installedAppId))
    if (installedApp == null || installedApp.userId != userId) {
      logger.warn { "Get configuration failed: installed app not found: $installedAppId for user: $userId" }
      return AppDataError.INSTALLED_APP_NOT_FOUND.left()
    }
    val version = resolveInstalledAppVersion(installedApp, appVersionRepository) ?: run {
      logger.warn { "Get configuration failed: version of installed app $installedAppId not found" }
      return AppDataError.INSTALLED_APP_NOT_FOUND.left()
    }
    val entity = version.configurationEntity ?: run {
      logger.warn { "Get configuration failed: version ${version.id.value} of installed app $installedAppId defines no configuration" }
      return AppDataError.CONFIGURATION_NOT_DEFINED.left()
    }
    return findOrCreate(installedApp, entity).right()
  }

  override fun configurationValues(installedApp: InstalledApp, version: AppVersion): Map<String, String?> {
    val entity = version.configurationEntity ?: return emptyMap()
    return existing(installedApp.id, entity)?.data ?: defaultData(entity)
  }

  private fun existing(installedAppId: InstalledAppId, entity: EntityDefinition): AppData? =
    appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entity.id).minByOrNull { it.createdAt }

  private fun findOrCreate(installedApp: InstalledApp, entity: EntityDefinition): AppData {
    existing(installedApp.id, entity)?.let { return it }
    val now = Instant.now()
    val appData = AppData(
      id = AppDataId(UUID.randomUUID().toString()),
      userId = installedApp.userId,
      installedAppId = installedApp.id,
      appVersion = installedApp.installedVersionNumber,
      lastValidatedWithVersion = installedApp.installedVersionNumber,
      entityType = entity.id,
      objectVersion = 1,
      createdAt = now,
      lastChangedAt = now,
      data = defaultData(entity),
    )
    appDataRepository.save(appData)
    logger.info { "Configuration created from defaults: ${appData.id.value} for installed app: ${installedApp.id.value} entity: ${entity.id.value}" }
    return appData
  }

  /** The stored values of a new configuration object: defaults (in storage form), smart defaults for top-level properties, OBJECT values assembled. */
  private fun defaultData(entity: EntityDefinition): Map<String, String?> {
    val smartDefaults = if (entity.properties.any { it.smartDefault != null }) smartDefault.computeSmartDefaults(entity, Clock.System.now()) else emptyMap()
    return entity.properties.associate { property ->
      val value = defaultValue(property)
      property.id.value to if (value is Map<*, *>) {
        @Suppress("UNCHECKED_CAST")
        encodeObjectValue(value as Map<String, Any?>)
      } else {
        (value as String?) ?: smartDefaults[property.id.value]?.takeIf { it.isNotBlank() }
      }
    }
  }

  /** A String for scalar properties, a nested Map (property id to value) for OBJECT properties with at least one default, null otherwise. */
  private fun defaultValue(property: Property): Any? = when (property.type) {
    PropertyType.OBJECT -> property.nestedProperties.associate { it.id.value to defaultValue(it) }.takeIf { values -> values.values.any { it != null } }
    else -> property.default
  }

  companion object : KLogging()
}
