package de.chrgroth.james.platform.domain.app

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.AppVersionError
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.Report
import de.chrgroth.james.platform.domain.model.app.ReportId
import de.chrgroth.james.platform.domain.model.app.VersionBumpResult
import de.chrgroth.james.platform.domain.model.app.VersionBumpType
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.port.`in`.app.AppVersionManagementPort
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.Instant
import java.util.UUID

@ApplicationScoped
@Suppress("Unused")
class AppVersionManagementService(
  private val appRepository: AppRepositoryPort,
  private val appVersionRepository: AppVersionRepositoryPort,
) : AppVersionManagementPort {

  override fun listVersions(appId: String): Either<DomainError, List<AppVersion>> {
    appRepository.findById(AppId(appId)) ?: run {
      logger.warn { "List versions failed: app not found: $appId" }
      return AppVersionError.APP_NOT_FOUND.left()
    }
    val versions = appVersionRepository.findAllByAppId(AppId(appId))
    val sorted = versions.sortedWith(
      compareBy<AppVersion> { if (it.status == AppVersionStatus.DRAFT) 0 else 1 }
        .thenByDescending { it.createdAt },
    )
    return sorted.right()
  }

  override fun createVersion(appId: String): Either<DomainError, AppVersion> {
    appRepository.findById(AppId(appId)) ?: run {
      logger.warn { "Create version failed: app not found: $appId" }
      return AppVersionError.APP_NOT_FOUND.left()
    }
    val existingVersions = appVersionRepository.findAllByAppId(AppId(appId))
    if (existingVersions.any { it.status == AppVersionStatus.DRAFT }) {
      logger.warn { "Create version failed: draft version already exists for app $appId" }
      return AppVersionError.DRAFT_VERSION_ALREADY_EXISTS.left()
    }
    val latestPublished = existingVersions
      .filter { it.status == AppVersionStatus.PUBLISHED }
      .maxByOrNull { it.createdAt }
    val newVersion = if (latestPublished != null) {
      latestPublished.copy(
        id = AppVersionId(UUID.randomUUID().toString()),
        versionNumber = null,
        releaseNotes = null,
        status = AppVersionStatus.DRAFT,
        createdAt = Instant.now(),
      )
    } else {
      AppVersion(
        id = AppVersionId(UUID.randomUUID().toString()),
        appId = AppId(appId),
        versionNumber = null,
        releaseNotes = null,
        entityDefinitions = emptyList(),
        reports = emptyList(),
        status = AppVersionStatus.DRAFT,
        createdAt = Instant.now(),
      )
    }
    appVersionRepository.save(newVersion)
    logger.info { "App version created (draft) for app $appId (${newVersion.id.value})" }
    return newVersion.right()
  }

  override fun getVersion(appId: String, versionId: String): Either<DomainError, AppVersion> {
    val version = appVersionRepository.findById(AppVersionId(versionId)) ?: run {
      logger.warn { "Get version failed: not found: $versionId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    if (version.appId != AppId(appId)) {
      logger.warn { "Get version failed: version $versionId does not belong to app $appId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    return version.right()
  }

  override fun publishVersion(appId: String, bumpType: String?, releaseNotes: String?): Either<DomainError, AppVersion> {
    val trimmedReleaseNotes = releaseNotes?.trim()?.takeIf { it.isNotBlank() } ?: run {
      logger.warn { "Publish version failed: blank release notes for app $appId" }
      return AppVersionError.BLANK_RELEASE_NOTES.left()
    }
    val allVersions = appVersionRepository.findAllByAppId(AppId(appId))
    val version = allVersions.find { it.status == AppVersionStatus.DRAFT } ?: run {
      logger.warn { "Publish version failed: no draft version found for app $appId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    val latestPublished = allVersions
      .filter { it.status == AppVersionStatus.PUBLISHED }
      .maxByOrNull { it.createdAt }
    val versionNumber = if (latestPublished == null) {
      VersionNumber(FIRST_VERSION)
    } else {
      if (!hasAnyChanges(latestPublished, version)) {
        logger.warn { "Publish version failed: no changes in entities or reports for app $appId" }
        return AppVersionError.NO_CHANGES.left()
      }
      val latestVersionNumber = latestPublished.versionNumber ?: run {
        logger.warn { "Publish version failed: latest published version has no version number for app $appId" }
        return AppVersionError.VERSION_NOT_FOUND.left()
      }
      val type = bumpType?.trim()?.takeIf { it.isNotBlank() }?.let {
        runCatching { VersionBumpType.valueOf(it.uppercase()) }.getOrNull()
      } ?: run {
        logger.warn { "Publish version failed: invalid bump type: $bumpType" }
        return AppVersionError.INVALID_BUMP_TYPE.left()
      }
      val hasBreaking = hasBreakingChanges(latestPublished, version)
      val (onBreaking, onFeature, onBugfix) = nextVersions(latestVersionNumber)
      when {
        hasBreaking -> onBreaking
        type == VersionBumpType.FEATURE -> onFeature
        else -> onBugfix
      }
    }
    if (allVersions.any { it.versionNumber == versionNumber && it.id != version.id }) {
      logger.warn { "Publish version failed: version number already exists: ${versionNumber.value} in app $appId" }
      return AppVersionError.VERSION_NUMBER_ALREADY_EXISTS.left()
    }
    val publishedVersion = version.copy(versionNumber = versionNumber, releaseNotes = trimmedReleaseNotes, status = AppVersionStatus.PUBLISHED)
    appVersionRepository.save(publishedVersion)
    logger.info { "App version published: ${versionNumber.value} (${version.id.value})" }
    return publishedVersion.right()
  }

  override fun computeVersionBump(appId: String, draftVersionId: String): Either<DomainError, VersionBumpResult> {
    appRepository.findById(AppId(appId)) ?: run {
      logger.warn { "Compute version bump failed: app not found: $appId" }
      return AppVersionError.APP_NOT_FOUND.left()
    }
    val draft = appVersionRepository.findById(AppVersionId(draftVersionId)) ?: run {
      logger.warn { "Compute version bump failed: version not found: $draftVersionId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    if (draft.appId != AppId(appId)) {
      logger.warn { "Compute version bump failed: version $draftVersionId does not belong to app $appId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    if (draft.status != AppVersionStatus.DRAFT) {
      logger.warn { "Compute version bump failed: version $draftVersionId is not in DRAFT status" }
      return AppVersionError.VERSION_NOT_IN_DRAFT.left()
    }
    val latestPublished = appVersionRepository.findAllByAppId(AppId(appId))
      .filter { it.status == AppVersionStatus.PUBLISHED }
      .maxByOrNull { it.createdAt }
    if (latestPublished == null) {
      val firstVersion = VersionNumber(FIRST_VERSION)
      logger.info { "Compute version bump: first release for app $appId → $FIRST_VERSION" }
      return VersionBumpResult(
        hasBreakingChanges = false,
        hasChanges = true,
        suggestedVersionOnBreaking = firstVersion,
        suggestedVersionOnFeature = firstVersion,
        suggestedVersionOnBugfix = firstVersion,
      ).right()
    }
    val hasChanges = hasAnyChanges(latestPublished, draft)
    val hasBreaking = hasBreakingChanges(latestPublished, draft)
    val latestVersionNumber = latestPublished.versionNumber ?: run {
      logger.warn { "Compute version bump failed: latest published version has no version number for app $appId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    val (onBreaking, onFeature, onBugfix) = nextVersions(latestVersionNumber)
    logger.info { "Compute version bump for app $appId: hasChanges=$hasChanges, breaking=$hasBreaking, breaking→${onBreaking.value}, feature→${onFeature.value}, bugfix→${onBugfix.value}" }
    return VersionBumpResult(
      hasBreakingChanges = hasBreaking,
      hasChanges = hasChanges,
      suggestedVersionOnBreaking = onBreaking,
      suggestedVersionOnFeature = onFeature,
      suggestedVersionOnBugfix = onBugfix,
    ).right()
  }

  override fun addEntity(appId: String, versionId: String, name: String): Either<DomainError, AppVersion> {
    if (name.isBlank()) {
      logger.warn { "Add entity failed: blank name" }
      return AppVersionError.BLANK_INPUT.left()
    }
    val version = getDraftVersion(appId, versionId) ?: return AppVersionError.VERSION_NOT_FOUND.left()
    if (version.entityDefinitions.any { it.name.equals(name.trim(), ignoreCase = true) }) {
      logger.warn { "Add entity failed: entity name already exists: $name in version $versionId" }
      return AppVersionError.ENTITY_NAME_ALREADY_EXISTS.left()
    }
    val newEntity = EntityDefinition(id = EntityDefinitionId(UUID.randomUUID().toString()), name = name.trim())
    val updated = version.copy(entityDefinitions = version.entityDefinitions + newEntity)
    appVersionRepository.save(updated)
    logger.info { "Entity added: ${name.trim()} to version $versionId" }
    return updated.right()
  }

  override fun deleteEntity(appId: String, versionId: String, entityId: String): Either<DomainError, AppVersion> {
    val version = getDraftVersion(appId, versionId) ?: return AppVersionError.VERSION_NOT_FOUND.left()
    if (version.entityDefinitions.none { it.id.value == entityId }) {
      logger.warn { "Delete entity failed: entity not found: $entityId in version $versionId" }
      return AppVersionError.ENTITY_NOT_FOUND.left()
    }
    val updated = version.copy(entityDefinitions = version.entityDefinitions.filter { it.id.value != entityId })
    appVersionRepository.save(updated)
    logger.info { "Entity deleted: $entityId from version $versionId" }
    return updated.right()
  }

  override fun addProperty(
    appId: String,
    versionId: String,
    entityId: String,
    name: String,
    type: String,
    nullable: Boolean,
  ): Either<DomainError, AppVersion> {
    if (name.isBlank()) {
      logger.warn { "Add property failed: blank name" }
      return AppVersionError.BLANK_INPUT.left()
    }
    val propertyType = runCatching { PropertyType.valueOf(type.uppercase()) }.getOrNull() ?: run {
      logger.warn { "Add property failed: invalid type: $type" }
      return AppVersionError.INVALID_PROPERTY_TYPE.left()
    }
    val version = getDraftVersion(appId, versionId) ?: return AppVersionError.VERSION_NOT_FOUND.left()
    val entity = version.entityDefinitions.find { it.id.value == entityId } ?: run {
      logger.warn { "Add property failed: entity not found: $entityId in version $versionId" }
      return AppVersionError.ENTITY_NOT_FOUND.left()
    }
    if (entity.properties.any { it.name.equals(name.trim(), ignoreCase = true) }) {
      logger.warn { "Add property failed: property name already exists: $name in entity $entityId" }
      return AppVersionError.PROPERTY_NAME_ALREADY_EXISTS.left()
    }
    val newProperty = Property(id = PropertyId(UUID.randomUUID().toString()), name = name.trim(), type = propertyType, nullable = nullable)
    val updatedEntity = entity.copy(properties = entity.properties + newProperty)
    val updated = version.copy(entityDefinitions = version.entityDefinitions.map { if (it.id.value == entityId) updatedEntity else it })
    appVersionRepository.save(updated)
    logger.info { "Property added: ${name.trim()} ($type) to entity $entityId in version $versionId" }
    return updated.right()
  }

  override fun updateProperty(
    appId: String,
    versionId: String,
    entityId: String,
    propertyId: String,
    name: String,
    type: String,
    nullable: Boolean,
  ): Either<DomainError, AppVersion> {
    if (name.isBlank()) {
      logger.warn { "Update property failed: blank name" }
      return AppVersionError.BLANK_INPUT.left()
    }
    val propertyType = runCatching { PropertyType.valueOf(type.uppercase()) }.getOrNull() ?: run {
      logger.warn { "Update property failed: invalid type: $type" }
      return AppVersionError.INVALID_PROPERTY_TYPE.left()
    }
    val version = getDraftVersion(appId, versionId) ?: return AppVersionError.VERSION_NOT_FOUND.left()
    val entity = version.entityDefinitions.find { it.id.value == entityId } ?: run {
      logger.warn { "Update property failed: entity not found: $entityId in version $versionId" }
      return AppVersionError.ENTITY_NOT_FOUND.left()
    }
    val property = entity.properties.find { it.id.value == propertyId } ?: run {
      logger.warn { "Update property failed: property not found: $propertyId in entity $entityId" }
      return AppVersionError.PROPERTY_NOT_FOUND.left()
    }
    if (entity.properties.any { it.id.value != propertyId && it.name.equals(name.trim(), ignoreCase = true) }) {
      logger.warn { "Update property failed: property name already exists: $name in entity $entityId" }
      return AppVersionError.PROPERTY_NAME_ALREADY_EXISTS.left()
    }
    val constraintsToKeep = if (propertyType == property.type) property.constraints else emptySet()
    val updatedProperty = property.copy(name = name.trim(), type = propertyType, nullable = nullable, constraints = constraintsToKeep)
    val updatedEntity = entity.copy(properties = entity.properties.map { if (it.id.value == propertyId) updatedProperty else it })
    val updated = version.copy(entityDefinitions = version.entityDefinitions.map { if (it.id.value == entityId) updatedEntity else it })
    appVersionRepository.save(updated)
    logger.info { "Property updated: $propertyId (${name.trim()}, $type) in entity $entityId in version $versionId" }
    return updated.right()
  }

  override fun setPropertyConstraints(
    appId: String,
    versionId: String,
    entityId: String,
    propertyId: String,
    constraints: Set<PropertyConstraint>,
  ): Either<DomainError, AppVersion> {
    val version = getDraftVersion(appId, versionId) ?: return AppVersionError.VERSION_NOT_FOUND.left()
    val entity = version.entityDefinitions.find { it.id.value == entityId } ?: run {
      logger.warn { "Set property constraints failed: entity not found: $entityId in version $versionId" }
      return AppVersionError.ENTITY_NOT_FOUND.left()
    }
    val property = entity.properties.find { it.id.value == propertyId } ?: run {
      logger.warn { "Set property constraints failed: property not found: $propertyId in entity $entityId" }
      return AppVersionError.PROPERTY_NOT_FOUND.left()
    }
    val allowedConstraintClasses = property.type.availableConstraints().toSet()
    val validConstraints = constraints.filter { c -> allowedConstraintClasses.any { it.isInstance(c) } }.toSet()
    val updatedProperty = property.copy(constraints = validConstraints)
    val updatedEntity = entity.copy(properties = entity.properties.map { if (it.id.value == propertyId) updatedProperty else it })
    val updated = version.copy(entityDefinitions = version.entityDefinitions.map { if (it.id.value == entityId) updatedEntity else it })
    appVersionRepository.save(updated)
    logger.info { "Property constraints set: $propertyId in entity $entityId in version $versionId (${validConstraints.size} constraints)" }
    return updated.right()
  }

  override fun deleteProperty(appId: String, versionId: String, entityId: String, propertyId: String): Either<DomainError, AppVersion> {
    val version = getDraftVersion(appId, versionId) ?: return AppVersionError.VERSION_NOT_FOUND.left()
    val entity = version.entityDefinitions.find { it.id.value == entityId } ?: run {
      logger.warn { "Delete property failed: entity not found: $entityId in version $versionId" }
      return AppVersionError.ENTITY_NOT_FOUND.left()
    }
    if (entity.properties.none { it.id.value == propertyId }) {
      logger.warn { "Delete property failed: property not found: $propertyId in entity $entityId" }
      return AppVersionError.PROPERTY_NOT_FOUND.left()
    }
    val updatedEntity = entity.copy(properties = entity.properties.filter { it.id.value != propertyId })
    val updated = version.copy(entityDefinitions = version.entityDefinitions.map { if (it.id.value == entityId) updatedEntity else it })
    appVersionRepository.save(updated)
    logger.info { "Property deleted: $propertyId from entity $entityId in version $versionId" }
    return updated.right()
  }

  override fun addReport(appId: String, versionId: String, name: String): Either<DomainError, AppVersion> {
    if (name.isBlank()) {
      logger.warn { "Add report failed: blank name" }
      return AppVersionError.BLANK_INPUT.left()
    }
    val version = getDraftVersion(appId, versionId) ?: return AppVersionError.VERSION_NOT_FOUND.left()
    if (version.reports.any { it.name.equals(name.trim(), ignoreCase = true) }) {
      logger.warn { "Add report failed: report name already exists: $name in version $versionId" }
      return AppVersionError.REPORT_NAME_ALREADY_EXISTS.left()
    }
    val newReport = Report(id = ReportId(UUID.randomUUID().toString()), name = name.trim())
    val updated = version.copy(reports = version.reports + newReport)
    appVersionRepository.save(updated)
    logger.info { "Report added: ${name.trim()} to version $versionId" }
    return updated.right()
  }

  override fun updateReport(
    appId: String,
    versionId: String,
    reportId: String,
    html: String,
    script: String,
  ): Either<DomainError, AppVersion> {
    val version = getDraftVersion(appId, versionId) ?: return AppVersionError.VERSION_NOT_FOUND.left()
    val report = version.reports.find { it.id.value == reportId } ?: run {
      logger.warn { "Update report failed: report not found: $reportId in version $versionId" }
      return AppVersionError.REPORT_NOT_FOUND.left()
    }
    val updatedReport = report.copy(html = html, script = script)
    val updated = version.copy(reports = version.reports.map { if (it.id.value == reportId) updatedReport else it })
    appVersionRepository.save(updated)
    logger.info { "Report $reportId updated in version $versionId" }
    return updated.right()
  }

  override fun deleteReport(appId: String, versionId: String, reportId: String): Either<DomainError, AppVersion> {
    val version = getDraftVersion(appId, versionId) ?: return AppVersionError.VERSION_NOT_FOUND.left()
    if (version.reports.none { it.id.value == reportId }) {
      logger.warn { "Delete report failed: report not found: $reportId in version $versionId" }
      return AppVersionError.REPORT_NOT_FOUND.left()
    }
    val updated = version.copy(reports = version.reports.filter { it.id.value != reportId })
    appVersionRepository.save(updated)
    logger.info { "Report deleted: $reportId from version $versionId" }
    return updated.right()
  }

  private fun getDraftVersion(appId: String, versionId: String): AppVersion? {
    val version = appVersionRepository.findById(AppVersionId(versionId)) ?: return null
    if (version.appId != AppId(appId)) return null
    if (version.status != AppVersionStatus.DRAFT) {
      logger.warn { "Operation failed: version $versionId is not in DRAFT status" }
      return null
    }
    return version
  }

  private fun hasAnyChanges(published: AppVersion, draft: AppVersion): Boolean =
    draft.entityDefinitions != published.entityDefinitions || draft.reports != published.reports

  private fun hasBreakingChanges(published: AppVersion, draft: AppVersion): Boolean {
    val publishedEntityIds = published.entityDefinitions.map { it.id }.toSet()
    val draftEntityIds = draft.entityDefinitions.map { it.id }.toSet()
    if (!draftEntityIds.containsAll(publishedEntityIds)) return true
    for (publishedEntity in published.entityDefinitions) {
      val draftEntity = draft.entityDefinitions.find { it.id == publishedEntity.id } ?: return true
      val publishedPropIds = publishedEntity.properties.map { it.id }.toSet()
      val draftPropIds = draftEntity.properties.map { it.id }.toSet()
      if (!draftPropIds.containsAll(publishedPropIds)) return true
      for (publishedProp in publishedEntity.properties) {
        val draftProp = draftEntity.properties.find { it.id == publishedProp.id } ?: return true
        if (draftProp.type != publishedProp.type) return true
        if (publishedProp.nullable && !draftProp.nullable) return true
        val addedConstraints = draftProp.constraints - publishedProp.constraints
        if (addedConstraints.any { isRestrictiveConstraint(it) }) return true
      }
    }
    return false
  }

  private fun isRestrictiveConstraint(constraint: PropertyConstraint): Boolean = when (constraint) {
    is PropertyConstraint.MinLong,
    is PropertyConstraint.MaxLong,
    is PropertyConstraint.MinDouble,
    is PropertyConstraint.MaxDouble,
    is PropertyConstraint.MinLength,
    is PropertyConstraint.MaxLength,
    is PropertyConstraint.Pattern,
    is PropertyConstraint.MinSize,
    is PropertyConstraint.MaxSize,
    -> true
    else -> false
  }

  private fun nextVersions(current: VersionNumber): Triple<VersionNumber, VersionNumber, VersionNumber> {
    val parts = current.value.split(".").map { it.toInt() }
    val major = parts[0]
    val minor = parts[1]
    val patch = parts[2]
    return Triple(
      VersionNumber("${major + 1}.0.0"),
      VersionNumber("$major.${minor + 1}.0"),
      VersionNumber("$major.$minor.${patch + 1}"),
    )
  }

  companion object : KLogging() {
    internal const val FIRST_VERSION = "0.1.0"
  }
}
