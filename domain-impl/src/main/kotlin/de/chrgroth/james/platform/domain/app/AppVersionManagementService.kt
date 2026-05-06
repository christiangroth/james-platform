package de.chrgroth.james.platform.domain.app

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.AppVersionError
import de.chrgroth.james.platform.domain.error.DisplayTextInvalidError
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
import de.chrgroth.james.platform.domain.model.app.VersionDiff
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.model.app.DiffLine
import de.chrgroth.james.platform.domain.model.app.DiffLineStatus
import de.chrgroth.james.platform.domain.model.app.DiffStatus
import de.chrgroth.james.platform.domain.model.app.SortCriteria
import de.chrgroth.james.platform.domain.model.app.SectionDiff
import de.chrgroth.james.platform.domain.port.`in`.app.AppVersionManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.PropertyConstraintPort
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.Instant
import java.util.UUID

@ApplicationScoped
@Suppress("Unused", "TooManyFunctions", "LargeClass")
class AppVersionManagementService(
  private val appRepository: AppRepositoryPort,
  private val appVersionRepository: AppVersionRepositoryPort,
  private val propertyConstraint: PropertyConstraintPort,
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

  override fun publishVersion(appId: String, bumpType: String?, releaseNotes: String): Either<DomainError, AppVersion> {
    val trimmedReleaseNotes = releaseNotes.trim().takeIf { it.isNotBlank() } ?: run {
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
    val invalidEntityNames = version.entityDefinitions.mapNotNull { entity ->
      val dt = entity.displayText ?: return@mapNotNull null
      val nonNullablePropNames = entity.properties.filter { !it.nullable }.map { it.name }.toSet()
      val usedPropNames = extractPropertyNames(dt)
      val invalidNames = usedPropNames - nonNullablePropNames
      if (invalidNames.isNotEmpty()) {
        logger.warn { "Publish version failed: entity ${entity.id.value} has invalid display text references: $invalidNames" }
        entity.name
      } else null
    }
    if (invalidEntityNames.isNotEmpty()) {
      return DisplayTextInvalidError(invalidEntityNames).left()
    }
    val publishedVersion = version.copy(versionNumber = versionNumber, releaseNotes = trimmedReleaseNotes, status = AppVersionStatus.PUBLISHED)
    appVersionRepository.save(publishedVersion)
    logger.info { "App version published: ${versionNumber.value} (${version.id.value})" }
    return publishedVersion.right()
  }

  override fun deleteDraftVersion(appId: String, versionId: String): Either<DomainError, Unit> {
    val version = getDraftVersion(appId, versionId) ?: return AppVersionError.VERSION_NOT_FOUND.left()
    appVersionRepository.delete(version.id)
    logger.info { "Draft version deleted: $versionId from app $appId" }
    return Unit.right()
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

  override fun reorderEntities(appId: String, versionId: String, entityIds: List<String>): Either<DomainError, AppVersion> {
    val version = getDraftVersion(appId, versionId) ?: return AppVersionError.VERSION_NOT_FOUND.left()
    val existingIds = version.entityDefinitions.map { it.id.value }.toSet()
    if (existingIds != entityIds.toSet() || entityIds.size != version.entityDefinitions.size) {
      logger.warn { "Reorder entities failed: entity IDs mismatch in version $versionId" }
      return AppVersionError.ENTITY_IDS_MISMATCH.left()
    }
    val entityById = version.entityDefinitions.associateBy { it.id.value }
    val reordered = entityIds.mapNotNull { entityById[it] }
    val updated = version.copy(entityDefinitions = reordered)
    appVersionRepository.save(updated)
    logger.info { "Entities reordered in version $versionId" }
    return updated.right()
  }

  override fun updateEntitySortCriteria(
    appId: String,
    versionId: String,
    entityId: String,
    sortBy: List<SortCriteria>,
  ): Either<DomainError, AppVersion> {
    val version = getDraftVersion(appId, versionId) ?: return AppVersionError.VERSION_NOT_FOUND.left()
    val entity = version.entityDefinitions.find { it.id.value == entityId } ?: run {
      logger.warn { "Update entity sort criteria failed: entity not found: $entityId in version $versionId" }
      return AppVersionError.ENTITY_NOT_FOUND.left()
    }
    val propIds = entity.properties.map { it.id.value }.toSet()
    val validSortBy = sortBy.filter { it.propertyId in propIds }
    val updatedEntity = entity.copy(sortBy = validSortBy)
    val updated = version.copy(entityDefinitions = version.entityDefinitions.map { if (it.id.value == entityId) updatedEntity else it })
    appVersionRepository.save(updated)
    logger.info { "Entity sort criteria updated: $entityId in version $versionId (${validSortBy.size} criteria)" }
    return updated.right()
  }

  override fun updateEntityDisplayText(
    appId: String,
    versionId: String,
    entityId: String,
    displayText: String?,
  ): Either<DomainError, AppVersion> {
    val version = getDraftVersion(appId, versionId) ?: return AppVersionError.VERSION_NOT_FOUND.left()
    val entity = version.entityDefinitions.find { it.id.value == entityId } ?: run {
      logger.warn { "Update entity display text failed: entity not found: $entityId in version $versionId" }
      return AppVersionError.ENTITY_NOT_FOUND.left()
    }
    val trimmedDisplayText = displayText?.trim()?.takeIf { it.isNotBlank() }
    if (trimmedDisplayText != null) {
      val nonNullablePropNames = entity.properties.filter { !it.nullable }.map { it.name }.toSet()
      val usedPropNames = extractPropertyNames(trimmedDisplayText)
      val invalidPropNames = usedPropNames - nonNullablePropNames
      if (invalidPropNames.isNotEmpty()) {
        logger.warn { "Update entity display text failed: template references nullable/unknown properties: $invalidPropNames in entity $entityId" }
        return AppVersionError.DISPLAY_TEXT_USES_NULLABLE_PROPERTY.left()
      }
    }
    val updatedEntity = entity.copy(displayText = trimmedDisplayText)
    val updated = version.copy(entityDefinitions = version.entityDefinitions.map { if (it.id.value == entityId) updatedEntity else it })
    appVersionRepository.save(updated)
    logger.info { "Entity display text updated: $entityId in version $versionId" }
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
    val newDisplayText = if (nullable && !property.nullable) {
      removePropertyFromDisplayText(entity.displayText, property.name)
    } else {
      entity.displayText
    }
    val updatedEntity = entity.copy(
      properties = entity.properties.map { if (it.id.value == propertyId) updatedProperty else it },
      displayText = newDisplayText,
    )
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

  override fun setPropertyDefault(
    appId: String,
    versionId: String,
    entityId: String,
    propertyId: String,
    default: String?,
  ): Either<DomainError, AppVersion> {
    val version = getDraftVersion(appId, versionId) ?: return AppVersionError.VERSION_NOT_FOUND.left()
    val entity = version.entityDefinitions.find { it.id.value == entityId } ?: run {
      logger.warn { "Set property default failed: entity not found: $entityId in version $versionId" }
      return AppVersionError.ENTITY_NOT_FOUND.left()
    }
    val property = entity.properties.find { it.id.value == propertyId } ?: run {
      logger.warn { "Set property default failed: property not found: $propertyId in entity $entityId" }
      return AppVersionError.PROPERTY_NOT_FOUND.left()
    }
    if (!property.type.supportsDefault()) {
      logger.warn { "Set property default failed: type ${property.type} does not support defaults: $propertyId" }
      return AppVersionError.DEFAULT_NOT_SUPPORTED.left()
    }
    val trimmedDefault = default?.takeIf { it.isNotBlank() }
    if (trimmedDefault != null) {
      val parsedValue = parseDefaultValue(property.type, trimmedDefault)
      if (parsedValue == null) {
        logger.warn { "Set property default failed: invalid default value '$trimmedDefault' for type ${property.type}: $propertyId" }
        return AppVersionError.DEFAULT_VALUE_INVALID.left()
      }
      val violations = propertyConstraint.checkValue(property, parsedValue, emptyList())
      if (violations.isNotEmpty()) {
        logger.warn { "Set property default failed: constraint violations for default value '$trimmedDefault': $violations" }
        return AppVersionError.DEFAULT_VALUE_INVALID.left()
      }
    }
    val updatedProperty = property.copy(default = trimmedDefault)
    val updatedEntity = entity.copy(properties = entity.properties.map { if (it.id.value == propertyId) updatedProperty else it })
    val updated = version.copy(entityDefinitions = version.entityDefinitions.map { if (it.id.value == entityId) updatedEntity else it })
    appVersionRepository.save(updated)
    logger.info { "Property default set: $propertyId in entity $entityId in version $versionId (default=$trimmedDefault)" }
    return updated.right()
  }

  private fun parseDefaultValue(type: PropertyType, value: String): Any? = when (type) {
    PropertyType.LONG -> value.toLongOrNull()
    PropertyType.DOUBLE -> value.toDoubleOrNull()
    PropertyType.BOOLEAN -> if (value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true)) {
      value.equals("true", ignoreCase = true)
    } else {
      null
    }
    else -> value
  }

  override fun setPropertySmartDefault(
    appId: String,
    versionId: String,
    entityId: String,
    propertyId: String,
    smartDefault: String?,
  ): Either<DomainError, AppVersion> {
    val version = getDraftVersion(appId, versionId) ?: return AppVersionError.VERSION_NOT_FOUND.left()
    val entity = version.entityDefinitions.find { it.id.value == entityId } ?: run {
      logger.warn { "Set property smart default failed: entity not found: $entityId in version $versionId" }
      return AppVersionError.ENTITY_NOT_FOUND.left()
    }
    val property = entity.properties.find { it.id.value == propertyId } ?: run {
      logger.warn { "Set property smart default failed: property not found: $propertyId in entity $entityId" }
      return AppVersionError.PROPERTY_NOT_FOUND.left()
    }
    val trimmedSmartDefault = smartDefault?.takeIf { it.isNotBlank() }
    if (trimmedSmartDefault != null && !property.type.supportsSmartDefault()) {
      logger.warn { "Set property smart default failed: type ${property.type} does not support smart defaults: $propertyId" }
      return AppVersionError.SMART_DEFAULT_NOT_SUPPORTED.left()
    }
    val updatedProperty = property.copy(smartDefault = trimmedSmartDefault)
    val updatedEntity = entity.copy(properties = entity.properties.map { if (it.id.value == propertyId) updatedProperty else it })
    val updated = version.copy(entityDefinitions = version.entityDefinitions.map { if (it.id.value == entityId) updatedEntity else it })
    appVersionRepository.save(updated)
    logger.info { "Property smart default set: $propertyId in entity $entityId in version $versionId (smartDefault=${trimmedSmartDefault?.let { "set" } ?: "cleared"})" }
    return updated.right()
  }

  override fun setPropertyValueProposals(
    appId: String,
    versionId: String,
    entityId: String,
    propertyId: String,
    valueProposals: List<String>,
  ): Either<DomainError, AppVersion> {
    val version = getDraftVersion(appId, versionId) ?: return AppVersionError.VERSION_NOT_FOUND.left()
    val entity = version.entityDefinitions.find { it.id.value == entityId } ?: run {
      logger.warn { "Set property value proposals failed: entity not found: $entityId in version $versionId" }
      return AppVersionError.ENTITY_NOT_FOUND.left()
    }
    val property = entity.properties.find { it.id.value == propertyId } ?: run {
      logger.warn { "Set property value proposals failed: property not found: $propertyId in entity $entityId" }
      return AppVersionError.PROPERTY_NOT_FOUND.left()
    }
    if (property.type != PropertyType.STRING) {
      logger.warn { "Set property value proposals failed: type ${property.type} does not support value proposals: $propertyId" }
      return AppVersionError.VALUE_PROPOSALS_NOT_SUPPORTED.left()
    }
    val validProposalIds = entity.properties.map { it.id.value }.toSet()
    val filteredProposals = valueProposals.filter { it in validProposalIds && it != propertyId }
    val updatedProperty = property.copy(valueProposals = filteredProposals)
    val updatedEntity = entity.copy(properties = entity.properties.map { if (it.id.value == propertyId) updatedProperty else it })
    val updated = version.copy(entityDefinitions = version.entityDefinitions.map { if (it.id.value == entityId) updatedEntity else it })
    appVersionRepository.save(updated)
    logger.info { "Property value proposals set: $propertyId in entity $entityId in version $versionId (${filteredProposals.size} proposals)" }
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
    val propertyName = entity.properties.first { it.id.value == propertyId }.name
    val updatedEntity = entity.copy(
      properties = entity.properties.filter { it.id.value != propertyId },
      displayText = removePropertyFromDisplayText(entity.displayText, propertyName),
    )
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

  private fun extractPropertyNames(template: String): Set<String> =
    Regex("\\{([^}]+)\\}").findAll(template)
      .map { it.groupValues[1] }
      .filter { it != "id" }
      .toSet()

  private fun removePropertyFromDisplayText(displayText: String?, propertyName: String): String? {
    if (displayText.isNullOrBlank()) return displayText
    val cleaned = displayText.replace(Regex("\\{${Regex.escape(propertyName)}\\}"), "").trim()
    return cleaned.takeIf { it.isNotBlank() }
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

  override fun getVersionDiff(appId: String, versionId: String): Either<DomainError, VersionDiff> {
    val version = appVersionRepository.findById(AppVersionId(versionId)) ?: run {
      logger.warn { "Get version diff failed: version not found: $versionId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    if (version.appId != AppId(appId)) {
      logger.warn { "Get version diff failed: version $versionId does not belong to app $appId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    if (version.status != AppVersionStatus.PUBLISHED && version.status != AppVersionStatus.DRAFT) {
      logger.warn { "Get version diff failed: version $versionId has unsupported status ${version.status}" }
      return AppVersionError.VERSION_NOT_PUBLISHED.left()
    }
    val predecessor = if (version.status == AppVersionStatus.DRAFT) {
      appVersionRepository.findAllByAppId(AppId(appId))
        .filter { it.status == AppVersionStatus.PUBLISHED }
        .maxByOrNull { it.createdAt }
    } else {
      appVersionRepository.findAllByAppId(AppId(appId))
        .filter { it.status == AppVersionStatus.PUBLISHED && it.createdAt < version.createdAt }
        .maxByOrNull { it.createdAt }
    } ?: run {
      logger.warn { "Get version diff failed: no predecessor found for version $versionId" }
      return AppVersionError.NO_PREDECESSOR_VERSION.left()
    }
    val entityDiffs = computeEntityDiffs(predecessor, version)
    val reportDiffs = computeReportDiffs(predecessor, version)
    return VersionDiff(version, predecessor, entityDiffs, reportDiffs).right()
  }

  private fun computeEntityDiffs(predecessor: AppVersion, version: AppVersion): List<SectionDiff> {
    val result = mutableListOf<SectionDiff>()
    val predMap = predecessor.entityDefinitions.associateBy { it.id }
    val versionMap = version.entityDefinitions.associateBy { it.id }

    // Removed entities (in predecessor but not in version)
    for (entity in predecessor.entityDefinitions.sortedBy { it.name }) {
      if (entity.id !in versionMap) {
        val lines = entityToDslLines(entity).map { DiffLine(it, DiffLineStatus.REMOVED) }
        result.add(SectionDiff(entity.name, DiffStatus.REMOVED, lines))
      }
    }

    // Added entities (in version but not in predecessor)
    for (entity in version.entityDefinitions.sortedBy { it.name }) {
      if (entity.id !in predMap) {
        val lines = entityToDslLines(entity).map { DiffLine(it, DiffLineStatus.ADDED) }
        result.add(SectionDiff(entity.name, DiffStatus.ADDED, lines))
      }
    }

    // Modified or unchanged entities (in both)
    for (entity in version.entityDefinitions.sortedBy { it.name }) {
      val predEntity = predMap[entity.id] ?: continue
      val oldLines = entityToDslLines(predEntity)
      val newLines = entityToDslLines(entity)
      val diffLines = diffLines(oldLines, newLines)
      val status = if (diffLines.any { it.status != DiffLineStatus.UNCHANGED }) DiffStatus.MODIFIED else DiffStatus.UNCHANGED
      if (status != DiffStatus.UNCHANGED) {
        result.add(SectionDiff(entity.name, status, diffLines))
      }
    }

    return result.sortedBy { it.name }
  }

  private fun computeReportDiffs(predecessor: AppVersion, version: AppVersion): List<SectionDiff> {
    val result = mutableListOf<SectionDiff>()
    val predMap = predecessor.reports.associateBy { it.id }
    val versionMap = version.reports.associateBy { it.id }

    // Removed reports (in predecessor but not in version)
    for (report in predecessor.reports.sortedBy { it.name }) {
      if (report.id !in versionMap) {
        val lines = reportToDslLines(report).map { DiffLine(it, DiffLineStatus.REMOVED) }
        result.add(SectionDiff(report.name, DiffStatus.REMOVED, lines))
      }
    }

    // Added reports (in version but not in predecessor)
    for (report in version.reports.sortedBy { it.name }) {
      if (report.id !in predMap) {
        val lines = reportToDslLines(report).map { DiffLine(it, DiffLineStatus.ADDED) }
        result.add(SectionDiff(report.name, DiffStatus.ADDED, lines))
      }
    }

    // Modified or unchanged reports (in both)
    for (report in version.reports.sortedBy { it.name }) {
      val predReport = predMap[report.id] ?: continue
      val oldLines = reportToDslLines(predReport)
      val newLines = reportToDslLines(report)
      val diffLines = diffLines(oldLines, newLines)
      val status = if (diffLines.any { it.status != DiffLineStatus.UNCHANGED }) DiffStatus.MODIFIED else DiffStatus.UNCHANGED
      if (status != DiffStatus.UNCHANGED) {
        result.add(SectionDiff(report.name, status, diffLines))
      }
    }

    return result.sortedBy { it.name }
  }

  private fun entityToDslLines(entity: EntityDefinition): List<String> {
    val lines = mutableListOf<String>()
    lines.add("entity ${entity.name} {")
    for (prop in entity.properties.sortedBy { it.name }) {
      val nullable = if (prop.nullable) "?" else "!"
      val constraintParts = prop.constraints
        .sortedWith(compareBy({ it.javaClass.name }, { it.toString() }))
        .map { constraintToString(it) }
      val constraintsStr = if (constraintParts.isNotEmpty()) " [${constraintParts.joinToString(", ")}]" else ""
      lines.add("  ${prop.name}: ${prop.type}$nullable$constraintsStr")
    }
    lines.add("}")
    return lines
  }

  private fun reportToDslLines(report: Report): List<String> {
    val lines = mutableListOf<String>()
    lines.add("report ${report.name} {")
    lines.add("  html:")
    lines.addAll(report.html.lines().map { "    $it" })
    lines.add("  script:")
    lines.addAll(report.script.lines().map { "    $it" })
    lines.add("}")
    return lines
  }

  private fun constraintToString(constraint: PropertyConstraint): String = when (constraint) {
    is PropertyConstraint.UniqueKey -> "unique-key"
    is PropertyConstraint.MinLong -> "min:${constraint.min}"
    is PropertyConstraint.MaxLong -> "max:${constraint.max}"
    is PropertyConstraint.MinDouble -> "min:${constraint.min}"
    is PropertyConstraint.MaxDouble -> "max:${constraint.max}"
    is PropertyConstraint.MinLength -> "min-length:${constraint.min}"
    is PropertyConstraint.MaxLength -> "max-length:${constraint.max}"
    is PropertyConstraint.Pattern -> "pattern:${constraint.regex}"
    is PropertyConstraint.MinSize -> "min-size:${constraint.min}"
    is PropertyConstraint.MaxSize -> "max-size:${constraint.max}"
  }

  @Suppress("NestedBlockDepth")
  private fun diffLines(oldLines: List<String>, newLines: List<String>): List<DiffLine> {
    val m = oldLines.size
    val n = newLines.size
    val dp = Array(m + 1) { IntArray(n + 1) }
    for (i in 1..m) {
      for (j in 1..n) {
        dp[i][j] = if (oldLines[i - 1] == newLines[j - 1]) dp[i - 1][j - 1] + 1
        else maxOf(dp[i - 1][j], dp[i][j - 1])
      }
    }
    val result = mutableListOf<DiffLine>()
    var i = m
    var j = n
    while (i > 0 || j > 0) {
      when {
        i > 0 && j > 0 && oldLines[i - 1] == newLines[j - 1] -> {
          result.add(DiffLine(oldLines[i - 1], DiffLineStatus.UNCHANGED))
          i--
          j--
        }
        j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j]) -> {
          result.add(DiffLine(newLines[j - 1], DiffLineStatus.ADDED))
          j--
        }
        else -> {
          result.add(DiffLine(oldLines[i - 1], DiffLineStatus.REMOVED))
          i--
        }
      }
    }
    return result.reversed()
  }

  companion object : KLogging() {
    internal const val FIRST_VERSION = "0.1.0"
  }
}
