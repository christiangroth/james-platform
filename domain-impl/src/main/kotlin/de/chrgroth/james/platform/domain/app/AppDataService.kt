package de.chrgroth.james.platform.domain.app

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.AppDataError
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.port.`in`.app.AppDataPort
import de.chrgroth.james.platform.domain.port.`in`.app.PropertyConstraintPort
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.Instant
import java.util.UUID

@ApplicationScoped
@Suppress("Unused")
class AppDataService(
  private val installedAppRepository: InstalledAppRepositoryPort,
  private val appVersionRepository: AppVersionRepositoryPort,
  private val appDataRepository: AppDataRepositoryPort,
  private val propertyConstraint: PropertyConstraintPort,
) : AppDataPort {

  override fun createAppData(
    userId: String,
    installedAppId: String,
    entityTypeId: String,
    data: Map<String, String>,
  ): Either<DomainError, AppData> {
    val installedApp = installedAppRepository.findById(InstalledAppId(installedAppId))
    if (installedApp == null || installedApp.userId != userId) {
      logger.warn { "Create app data failed: installed app not found: $installedAppId for user: $userId" }
      return AppDataError.INSTALLED_APP_NOT_FOUND.left()
    }

    val appVersion = appVersionRepository.findByAppIdAndVersionNumber(installedApp.appId, installedApp.installedVersionNumber)
    if (appVersion == null) {
      logger.warn { "Create app data failed: app version not found for installed app: $installedAppId" }
      return AppDataError.INSTALLED_APP_NOT_FOUND.left()
    }

    val entityDef = appVersion.entityDefinitions.find { it.id.value == entityTypeId }
    if (entityDef == null) {
      logger.warn { "Create app data failed: entity not found: $entityTypeId in version ${appVersion.id.value}" }
      return AppDataError.ENTITY_NOT_FOUND.left()
    }

    val existingValues = appDataRepository.findAllByInstalledAppIdAndEntityType(
      InstalledAppId(installedAppId),
      EntityDefinitionId(entityTypeId),
    )

    val parsedData = mutableMapOf<String, String?>()
    for (property in entityDef.properties) {
      val rawValue = data["prop_${property.id.value}"]
      val parsedValue = parseValue(property, rawValue)
      val violations = propertyConstraint.checkValue(
        property,
        parsedValue,
        existingValues.mapNotNull { it.data[property.id.value]?.let { v -> parseValue(property, v) } },
      )
      if (violations.isNotEmpty()) {
        logger.warn { "Create app data failed: constraint violations for property ${property.name}: $violations" }
        return AppDataError.CONSTRAINT_VIOLATION.left()
      }
      parsedData[property.id.value] = rawValue?.takeIf { it.isNotBlank() }
    }

    val now = Instant.now()
    val appData = AppData(
      id = AppDataId(UUID.randomUUID().toString()),
      userId = userId,
      installedAppId = InstalledAppId(installedAppId),
      appVersion = installedApp.installedVersionNumber,
      entityType = EntityDefinitionId(entityTypeId),
      objectVersion = 1,
      createdAt = now,
      lastChangedAt = now,
      data = parsedData,
    )
    appDataRepository.save(appData)
    logger.info { "App data created: ${appData.id.value} for installed app: $installedAppId entity: $entityTypeId" }
    return appData.right()
  }

  override fun listAppData(userId: String, installedAppId: String): Either<DomainError, List<AppData>> {
    val installedApp = installedAppRepository.findById(InstalledAppId(installedAppId))
    if (installedApp == null || installedApp.userId != userId) {
      logger.warn { "List app data failed: installed app not found: $installedAppId for user: $userId" }
      return AppDataError.INSTALLED_APP_NOT_FOUND.left()
    }
    val appDataList = appDataRepository.findAllByInstalledAppId(InstalledAppId(installedAppId))
    return appDataList.sortedByDescending { it.lastChangedAt }.right()
  }

  override fun getAppData(userId: String, installedAppId: String, dataId: String): Either<DomainError, AppData> {
    val installedApp = installedAppRepository.findById(InstalledAppId(installedAppId))
    if (installedApp == null || installedApp.userId != userId) {
      logger.warn { "Get app data failed: installed app not found: $installedAppId for user: $userId" }
      return AppDataError.INSTALLED_APP_NOT_FOUND.left()
    }
    val appDataItem = appDataRepository.findById(AppDataId(dataId))
    if (appDataItem == null || appDataItem.installedAppId.value != installedAppId) {
      logger.warn { "Get app data failed: app data not found: $dataId for installed app: $installedAppId" }
      return AppDataError.APP_DATA_NOT_FOUND.left()
    }
    return appDataItem.right()
  }

  override fun updateAppData(
    userId: String,
    installedAppId: String,
    dataId: String,
    data: Map<String, String>,
  ): Either<DomainError, AppData> {
    val installedApp = installedAppRepository.findById(InstalledAppId(installedAppId))
    if (installedApp == null || installedApp.userId != userId) {
      logger.warn { "Update app data failed: installed app not found: $installedAppId for user: $userId" }
      return AppDataError.INSTALLED_APP_NOT_FOUND.left()
    }

    val existingAppData = appDataRepository.findById(AppDataId(dataId))
    if (existingAppData == null || existingAppData.installedAppId.value != installedAppId) {
      logger.warn { "Update app data failed: app data not found: $dataId for installed app: $installedAppId" }
      return AppDataError.APP_DATA_NOT_FOUND.left()
    }

    val appVersion = appVersionRepository.findByAppIdAndVersionNumber(installedApp.appId, installedApp.installedVersionNumber)
    if (appVersion == null) {
      logger.warn { "Update app data failed: app version not found for installed app: $installedAppId" }
      return AppDataError.INSTALLED_APP_NOT_FOUND.left()
    }

    val entityDef = appVersion.entityDefinitions.find { it.id.value == existingAppData.entityType.value }
    if (entityDef == null) {
      logger.warn { "Update app data failed: entity not found: ${existingAppData.entityType.value} in version ${appVersion.id.value}" }
      return AppDataError.ENTITY_NOT_FOUND.left()
    }

    val existingValues = appDataRepository.findAllByInstalledAppIdAndEntityType(
      InstalledAppId(installedAppId),
      existingAppData.entityType,
    ).filter { it.id.value != dataId }

    val parsedData = mutableMapOf<String, String?>()
    for (property in entityDef.properties) {
      val rawValue = data["prop_${property.id.value}"]
      val parsedValue = parseValue(property, rawValue)
      val violations = propertyConstraint.checkValue(
        property,
        parsedValue,
        existingValues.mapNotNull { it.data[property.id.value]?.let { v -> parseValue(property, v) } },
      )
      if (violations.isNotEmpty()) {
        logger.warn { "Update app data failed: constraint violations for property ${property.name}: $violations" }
        return AppDataError.CONSTRAINT_VIOLATION.left()
      }
      parsedData[property.id.value] = rawValue?.takeIf { it.isNotBlank() }
    }

    val updatedAppData = existingAppData.copy(
      objectVersion = existingAppData.objectVersion + 1,
      lastChangedAt = Instant.now(),
      data = parsedData,
    )
    appDataRepository.save(updatedAppData)
    logger.info { "App data updated: ${updatedAppData.id.value} for installed app: $installedAppId" }
    return updatedAppData.right()
  }

  override fun deleteAppData(userId: String, installedAppId: String, dataId: String): Either<DomainError, Unit> {
    val installedApp = installedAppRepository.findById(InstalledAppId(installedAppId))
    if (installedApp == null || installedApp.userId != userId) {
      logger.warn { "Delete app data failed: installed app not found: $installedAppId for user: $userId" }
      return AppDataError.INSTALLED_APP_NOT_FOUND.left()
    }
    val appDataItem = appDataRepository.findById(AppDataId(dataId))
    if (appDataItem == null || appDataItem.installedAppId.value != installedAppId) {
      logger.warn { "Delete app data failed: app data not found: $dataId for installed app: $installedAppId" }
      return AppDataError.APP_DATA_NOT_FOUND.left()
    }
    appDataRepository.delete(AppDataId(dataId))
    logger.info { "App data deleted: $dataId for installed app: $installedAppId" }
    return Unit.right()
  }

  private fun parseValue(property: Property, rawValue: String?): Any? {
    if (rawValue.isNullOrBlank()) return null
    return when (property.type) {
      PropertyType.LONG -> rawValue.toLongOrNull()
      PropertyType.DOUBLE -> rawValue.toDoubleOrNull()
      PropertyType.BOOLEAN -> rawValue.equals("true", ignoreCase = true)
      else -> rawValue
    }
  }

  companion object : KLogging()
}
