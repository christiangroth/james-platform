package de.chrgroth.james.platform.domain.app

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.AppDataConstraintViolationError
import de.chrgroth.james.platform.domain.error.AppDataError
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.error.PathedConstraintViolation
import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.referenceLookupTarget
import de.chrgroth.james.platform.domain.model.app.REFERENCE_NEW_FORM_PREFIX
import de.chrgroth.james.platform.domain.model.app.REFERENCE_LOOKUP_FORM_PREFIX
import de.chrgroth.james.platform.domain.model.app.decodeListValue
import de.chrgroth.james.platform.domain.model.app.decodeObjectValue
import de.chrgroth.james.platform.domain.model.app.encodeListValue
import de.chrgroth.james.platform.domain.model.app.sortListItems
import de.chrgroth.james.platform.domain.model.app.encodeObjectValue
import de.chrgroth.james.platform.domain.model.app.isObjectList
import de.chrgroth.james.platform.domain.model.app.parseUnitValue
import de.chrgroth.james.platform.domain.model.app.uniqueKeyComparisonValues
import de.chrgroth.james.platform.domain.model.app.uniqueKeyProperties
import de.chrgroth.james.platform.domain.port.`in`.app.AppDataPort
import de.chrgroth.james.platform.domain.port.`in`.app.PropertyConstraintPort
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@ApplicationScoped
@Suppress("Unused")
class AppDataService(
  private val installedAppRepository: InstalledAppRepositoryPort,
  private val appVersionRepository: AppVersionRepositoryPort,
  private val appDataRepository: AppDataRepositoryPort,
  private val propertyConstraint: PropertyConstraintPort,
  private val aggregationInlineUpdate: AggregationInlineUpdateService,
) : AppDataPort {

  override fun createAppData(
    userId: String,
    installedAppId: String,
    entityTypeId: String,
    data: Map<String, List<String>>,
  ): Either<DomainError, AppData> {
    val installedApp = installedAppRepository.findById(InstalledAppId(installedAppId))
    if (installedApp == null || installedApp.userId != userId) {
      logger.warn { "Create app data failed: installed app not found: $installedAppId for user: $userId" }
      return AppDataError.INSTALLED_APP_NOT_FOUND.left()
    }

    val appVersion = resolveAppVersion(installedApp)
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
    if (entityDef.configuration && existingValues.isNotEmpty()) {
      logger.warn { "Create app data failed: configuration object of installed app $installedAppId already exists (entity $entityTypeId)" }
      return AppDataError.CONFIGURATION_ALREADY_EXISTS.left()
    }

    val lookups = resolveReferenceLookups(installedApp, appVersion, entityDef, data)
    val validated = validateFormData(entityDef, lookups.data, existingValues, installedApp.id, lookups.pendingIds)
    val violationError = lookups.combinedViolations(validated)
    if (violationError != null) {
      logger.warn { "Create app data failed: constraint violations for properties ${violationError.propertyViolations.keys}" }
      return violationError.left()
    }

    savePendingReferenceTargets(installedApp, appVersion, lookups.pendingCreates)
    val appData = newAppData(installedApp, entityDef, validated.data)
    appDataRepository.save(appData)
    aggregationInlineUpdate.onAppDataCreated(InstalledAppId(installedAppId), appVersion.entityDefinitions, entityDef, appData)
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
    data: Map<String, List<String>>,
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

    val appVersion = resolveAppVersion(installedApp)
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

    val lookups = resolveReferenceLookups(installedApp, appVersion, entityDef, data)
    val validated = validateFormData(entityDef, lookups.data, existingValues, installedApp.id, lookups.pendingIds)
    val violationError = lookups.combinedViolations(validated)
    if (violationError != null) {
      logger.warn { "Update app data failed: constraint violations for properties ${violationError.propertyViolations.keys}" }
      return violationError.left()
    }

    savePendingReferenceTargets(installedApp, appVersion, lookups.pendingCreates)
    val updatedAppData = existingAppData.copy(
      objectVersion = existingAppData.objectVersion + 1,
      lastChangedAt = Instant.now(),
      appVersion = installedApp.installedVersionNumber,
      lastValidatedWithVersion = installedApp.installedVersionNumber,
      data = validated.data,
    )
    appDataRepository.save(updatedAppData)
    aggregationInlineUpdate.onAppDataUpdated(InstalledAppId(installedAppId), appVersion.entityDefinitions, entityDef, existingAppData, updatedAppData)
    logger.info { "App data updated: ${updatedAppData.id.value} for installed app: $installedAppId" }
    return updatedAppData.right()
  }

  override fun deleteAppData(userId: String, installedAppId: String, dataId: String): Either<DomainError, Int> {
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

    val appVersion = resolveAppVersion(installedApp)
    if (appVersion == null) {
      logger.warn { "Delete app data failed: app version not found for installed app: $installedAppId" }
      return AppDataError.INSTALLED_APP_NOT_FOUND.left()
    }
    if (appVersion.entityDefinitions.any { it.id == appDataItem.entityType && it.configuration }) {
      logger.warn { "Delete app data failed: $dataId is the configuration object of installed app $installedAppId" }
      return AppDataError.CONFIGURATION_NOT_DELETABLE.left()
    }

    // Collect all (referencingData, property) pairs where a REF property value equals dataId
    val allData = appDataRepository.findAllByInstalledAppId(InstalledAppId(installedAppId))
    val references = mutableListOf<PropertyReference>()
    for (entityDef in appVersion.entityDefinitions) {
      val refProperties = entityDef.properties.filter { it.type == PropertyType.REF }
      if (refProperties.isEmpty()) continue
      val entityData = allData.filter { it.entityType == entityDef.id && it.id.value != dataId }
      for (item in entityData) {
        for (refProp in refProperties) {
          if (item.data[refProp.id.value] == dataId) {
            references += PropertyReference(item, refProp)
          }
        }
      }
    }

    // Reject deletion if any referencing property is non-nullable
    if (references.any { !it.property.nullable }) {
      logger.warn { "Delete app data failed: referenced by non-nullable property: $dataId for installed app: $installedAppId" }
      return AppDataError.REFERENCED_BY_NON_NULLABLE_PROPERTY.left()
    }

    // Null out all nullable references before deleting
    val now = Instant.now()
    val entityDefsById = appVersion.entityDefinitions.associateBy { it.id }
    for ((referencingData, refProp) in references) {
      val updatedData = referencingData.data.toMutableMap()
      updatedData[refProp.id.value] = null
      val updatedReferencingData = referencingData.copy(objectVersion = referencingData.objectVersion + 1, lastChangedAt = now, data = updatedData)
      appDataRepository.save(updatedReferencingData)
      entityDefsById[referencingData.entityType]?.let { referencingEntityDef ->
        aggregationInlineUpdate.onAppDataUpdated(InstalledAppId(installedAppId), appVersion.entityDefinitions, referencingEntityDef, referencingData, updatedReferencingData)
      }
    }

    appDataRepository.delete(AppDataId(dataId))
    entityDefsById[appDataItem.entityType]?.let { deletedEntityDef ->
      aggregationInlineUpdate.onAppDataDeleted(InstalledAppId(installedAppId), appVersion.entityDefinitions, deletedEntityDef, appDataItem)
    }
    logger.info { "App data deleted: $dataId for installed app: $installedAppId (${references.size} reference(s) cleared)" }
    return references.size.right()
  }

  override fun validateEntityData(entityDef: EntityDefinition, data: Map<String, String?>, installedAppId: String, excludingDataId: String?): Either<DomainError, Unit> {
    val existingValues = appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId(installedAppId), entityDef.id)
      .filter { excludingDataId == null || it.id.value != excludingDataId }

    val allViolations = mutableMapOf<String, List<PropertyConstraintViolation>>()
    val allPathedViolations = mutableListOf<PathedConstraintViolation>()
    val uniqueKeyValues = uniqueKeyComparison(entityDef, existingValues) { parseValue(it, data[it.id.value]) }
    for (property in entityDef.properties) {
      val storedValue = data[property.id.value]
      val parsedValue = parseValue(property, storedValue)
      val existingParsedValues = uniqueKeyValues(property)
      val extraViolations = referenceViolations(property, storedValue, InstalledAppId(installedAppId), emptySet())
      val violations = propertyConstraint.checkValue(property, parsedValue, existingParsedValues) + extraViolations
      if (violations.isNotEmpty()) {
        allViolations[property.id.value] = violations
        allPathedViolations += propertyConstraint.checkValueWithPaths(property, parsedValue, existingParsedValues)
        allPathedViolations += extraViolations.map { PathedConstraintViolation(property.name, it) }
      }
    }
    if (allViolations.isNotEmpty()) {
      return AppDataConstraintViolationError(allViolations, allPathedViolations).left()
    }
    return Unit.right()
  }

  /** Stored property values of one validated form, or the violations that prevent storing it. */
  private data class ValidatedFormData(
    val data: Map<String, String?>,
    val violations: Map<String, List<PropertyConstraintViolation>>,
    val pathedViolations: List<PathedConstraintViolation>,
  )

  /**
   * Converts and validates submitted form [data] for [entityDef] against its [existing] objects (unique keys) — the shared core of
   * [createAppData], [updateAppData] and auto-creating reference lookup targets. [pendingIds] are target ids created in the same save.
   * Non-nullable properties must carry a value ([PropertyConstraintViolation.RequiredValueViolation]); unlike [validateEntityData], which
   * re-validates stored data on upgrades and deliberately tolerates legacy gaps.
   */
  private fun validateFormData(
    entityDef: EntityDefinition,
    data: Map<String, List<String>>,
    existing: List<AppData>,
    installedAppId: InstalledAppId,
    pendingIds: Set<String>,
  ): ValidatedFormData {
    val parsedData = mutableMapOf<String, String?>()
    val allViolations = mutableMapOf<String, List<PropertyConstraintViolation>>()
    val allPathedViolations = mutableListOf<PathedConstraintViolation>()
    val converted = entityDef.properties.associate { it.id to if (it.isObjectList) objectListValue(it, data) else unitConvertedValue(it, storedValueFor(it, data)) }
    val uniqueKeyValues = uniqueKeyComparison(entityDef, existing) { parseValue(it, converted.getValue(it.id).first) }
    for (property in entityDef.properties) {
      val (storedValue, unitViolations) = converted.getValue(property.id)
      val parsedValue = parseValue(property, storedValue)
      val existingParsedValues = uniqueKeyValues(property)
      val extraViolations = requiredViolations(property, storedValue) + referenceViolations(property, storedValue, installedAppId, pendingIds) + unitViolations
      val violations = propertyConstraint.checkValue(property, parsedValue, existingParsedValues) + extraViolations
      if (violations.isNotEmpty()) {
        allViolations[property.id.value] = violations
        allPathedViolations += propertyConstraint.checkValueWithPaths(property, parsedValue, existingParsedValues)
        allPathedViolations += extraViolations.map { PathedConstraintViolation(property.name, it) }
      }
      parsedData[property.id.value] = storedValue
    }
    return ValidatedFormData(parsedData, allViolations, allPathedViolations)
  }

  private fun requiredViolations(property: Property, storedValue: String?): List<PropertyConstraintViolation> =
    if (!property.nullable && property.type != PropertyType.BOOLEAN && storedValue.isNullOrBlank()) {
      listOf(PropertyConstraintViolation.RequiredValueViolation)
    } else {
      emptyList()
    }

  /** A target object auto-created by a reference lookup, validated but not yet saved. */
  private data class PendingReferenceTarget(val entityDef: EntityDefinition, val appData: AppData)

  /** Form data with every reference lookup resolved into its REF id, plus the target objects to create first and lookup violations. */
  private data class ResolvedReferenceLookups(
    val data: Map<String, List<String>>,
    val pendingCreates: List<PendingReferenceTarget>,
    val violations: Map<String, List<PropertyConstraintViolation>>,
    val pathedViolations: List<PathedConstraintViolation>,
  ) {
    val pendingIds: Set<String> get() = pendingCreates.map { it.appData.id.value }.toSet()
  }

  /**
   * Resolves the lookup text (`reflookup_<id>`) of every REF property with a valid [Property.referenceLookup] into the id of the target
   * object whose lookup property carries that text (exact match first, else a single case-insensitive match). Unknown texts either
   * report [PropertyConstraintViolation.ReferenceLookupNotFoundViolation] or — with auto-create — validate a new target object from the
   * text plus its further `refnew_<id>.<targetPropertyId>` values, which is only saved once the whole form is valid (ADR 0026).
   * Properties without a submitted lookup key keep their regular `prop_<id>` id value (e.g. API clients).
   */
  private fun resolveReferenceLookups(
    installedApp: InstalledApp,
    appVersion: AppVersion,
    entityDef: EntityDefinition,
    data: Map<String, List<String>>,
  ): ResolvedReferenceLookups {
    val resolvedData = data.toMutableMap()
    val pendingCreates = mutableListOf<PendingReferenceTarget>()
    val pendingIdsByText = mutableMapOf<Pair<EntityDefinitionId, String>, String>()
    val violations = mutableMapOf<String, List<PropertyConstraintViolation>>()
    val pathedViolations = mutableListOf<PathedConstraintViolation>()
    for (property in entityDef.properties) {
      val lookupKey = "$REFERENCE_LOOKUP_FORM_PREFIX${property.id.value}"
      if (lookupKey !in data) continue
      val (target, lookupProperty) = property.referenceLookupTarget(appVersion.entityDefinitions) ?: continue
      val formKey = "prop_${property.id.value}"
      val text = data[lookupKey].orEmpty().firstOrNull { it.isNotBlank() }?.trim()
      if (text == null) {
        resolvedData[formKey] = emptyList()
        continue
      }
      val existingTargets = appDataRepository.findAllByInstalledAppIdAndEntityType(installedApp.id, target.id)
      val match = existingTargets.find { it.data[lookupProperty.id.value] == text }
        ?: existingTargets.filter { it.data[lookupProperty.id.value].equals(text, ignoreCase = true) }.singleOrNull()
      val pendingId = pendingIdsByText[target.id to text.lowercase()]
      when {
        match != null -> resolvedData[formKey] = listOf(match.id.value)
        pendingId != null -> resolvedData[formKey] = listOf(pendingId)
        property.referenceLookup?.autoCreate != true -> {
          violations[property.id.value] = listOf(PropertyConstraintViolation.ReferenceLookupNotFoundViolation)
          pathedViolations += PathedConstraintViolation(property.name, PropertyConstraintViolation.ReferenceLookupNotFoundViolation)
        }
        else -> {
          val newPrefix = "$REFERENCE_NEW_FORM_PREFIX${property.id.value}."
          val targetData = data.filterKeys { it.startsWith(newPrefix) }.mapKeys { "prop_${it.key.removePrefix(newPrefix)}" } +
            ("prop_${lookupProperty.id.value}" to listOf(text))
          val pendingIds = pendingCreates.map { it.appData.id.value }.toSet()
          val validated = validateFormData(target, targetData, existingTargets, installedApp.id, pendingIds)
          if (validated.violations.isEmpty()) {
            val created = newAppData(installedApp, target, validated.data)
            pendingCreates += PendingReferenceTarget(target, created)
            pendingIdsByText[target.id to text.lowercase()] = created.id.value
            resolvedData[formKey] = listOf(created.id.value)
          } else {
            violations[property.id.value] = validated.violations.values.flatten()
            pathedViolations += validated.pathedViolations.map { it.copy(path = "${property.name} › ${it.path}") }
          }
        }
      }
    }
    return ResolvedReferenceLookups(resolvedData, pendingCreates, violations, pathedViolations)
  }

  /** Saves the target objects auto-created by reference lookups — only called once the referencing object itself validated. */
  private fun savePendingReferenceTargets(installedApp: InstalledApp, appVersion: AppVersion, pendingCreates: List<PendingReferenceTarget>) {
    for (pending in pendingCreates) {
      appDataRepository.save(pending.appData)
      aggregationInlineUpdate.onAppDataCreated(installedApp.id, appVersion.entityDefinitions, pending.entityDef, pending.appData)
      logger.info { "App data auto-created by reference lookup: ${pending.appData.id.value} for installed app: ${installedApp.id.value} entity: ${pending.entityDef.id.value}" }
    }
  }

  private fun newAppData(installedApp: InstalledApp, entityDef: EntityDefinition, data: Map<String, String?>): AppData {
    val now = Instant.now()
    return AppData(
      id = AppDataId(UUID.randomUUID().toString()),
      userId = installedApp.userId,
      installedAppId = installedApp.id,
      appVersion = installedApp.installedVersionNumber,
      lastValidatedWithVersion = installedApp.installedVersionNumber,
      entityType = entityDef.id,
      objectVersion = 1,
      createdAt = now,
      lastChangedAt = now,
      data = data,
    )
  }

  /**
   * All violations of a form: those of its reference lookups plus those of the [validated] form data, or null if there are none. A lookup
   * that failed already explains why its REF property stayed empty, so the property's own [PropertyConstraintViolation.RequiredValueViolation]
   * is not reported on top.
   */
  private fun ResolvedReferenceLookups.combinedViolations(validated: ValidatedFormData): AppDataConstraintViolationError? {
    val required = PropertyConstraintViolation.RequiredValueViolation
    val failedLookupNames = pathedViolationsOwners()
    val formViolations = validated.violations.mapValues { (propertyId, list) -> if (propertyId in violations) list - required else list }
    val formPathed = validated.pathedViolations.filterNot { it.violation == required && it.path in failedLookupNames }
    val all = (violations.keys + formViolations.keys).associateWith { (violations[it].orEmpty() + formViolations[it].orEmpty()).distinct() }
      .filterValues { it.isNotEmpty() }
    return if (all.isEmpty()) null else AppDataConstraintViolationError(all, pathedViolations + formPathed)
  }

  private fun ResolvedReferenceLookups.pathedViolationsOwners(): Set<String> = pathedViolations.map { it.path.substringBefore(" › ") }.toSet()

  /**
   * Builds the per-property `existingValues` for the `UniqueKey` check of a candidate object (parsed key values via [candidateValue]) against
   * [existing] objects of [entityDef]: all key properties together form one composite key, see [uniqueKeyProperties].
   */
  private fun uniqueKeyComparison(entityDef: EntityDefinition, existing: List<AppData>, candidateValue: (Property) -> Any?): (Property) -> List<Any?> {
    val keyProperties = entityDef.uniqueKeyProperties
    if (keyProperties.isEmpty()) return { emptyList() }
    val candidate = keyProperties.associate { it.id to candidateValue(it) }
    val existingKeys = existing.map { appData -> keyProperties.associate { it.id to appData.data[it.id.value]?.let { v -> parseValue(it, v) } } }
    return { property -> uniqueKeyComparisonValues(property, keyProperties, candidate, existingKeys) }
  }

  /**
   * The stored value of a LIST of OBJECTs from its submitted form fields: one `prop_<listId>.<fieldId>` value per item and field, aligned by
   * position (as rendered by object-property-fields.js and flattened by the token API). Items whose non-yes/no fields are all blank are
   * dropped; the fields' units are converted and their required values checked per item. Every item is stored as JSON object by field id.
   */
  private fun objectListValue(property: Property, data: Map<String, List<String>>): Pair<String?, List<PropertyConstraintViolation>> {
    val columns = property.nestedProperties.associateWith { data["prop_${property.id.value}.${it.id.value}"].orEmpty() }
    val rows = columns.values.maxOfOrNull { it.size } ?: 0
    val violations = mutableListOf<PropertyConstraintViolation>()
    val items = (0 until rows).mapNotNull { index ->
      val raw = columns.mapValues { (_, values) -> values.getOrNull(index)?.takeIf { it.isNotBlank() } }
      if (raw.none { (field, value) -> field.type != PropertyType.BOOLEAN && value != null }) return@mapNotNull null
      raw.entries.associate { (field, value) ->
        val (converted, unitViolations) = unitConvertedValue(field, value)
        violations += unitViolations + requiredViolations(field, converted)
        field.id.value to converted
      }
    }
    return (if (items.isEmpty()) null else encodeListValue(items.map { encodeObjectValue(it) })) to violations
  }

  /** Encodes the raw form values submitted for a top-level property into the single string stored in [AppData.data]. */
  private fun storedValueFor(property: Property, data: Map<String, List<String>>): String? {
    val raw = rawPropertyValue(property, data, "prop_")
    @Suppress("UNCHECKED_CAST")
    return if (raw is Map<*, *>) encodeObjectValue(raw as Map<String, Any?>) else raw as String?
  }

  /**
   * Resolves the raw value submitted for [property] under [keyPrefix], recursing into nested properties for an OBJECT property.
   * Returns a String for scalar/LIST properties, a (possibly nested) Map for OBJECT properties, or null if nothing was submitted.
   */
  private fun rawPropertyValue(property: Property, data: Map<String, List<String>>, keyPrefix: String): Any? {
    val key = "$keyPrefix${property.id.value}"
    return when (property.type) {
      PropertyType.LIST -> {
        // Dates, times and numbers are kept ascending (e.g. a holiday added later lands in place, not at the end); other lists keep their entry order.
        val nonBlankValues = sortListItems(property.listItemType, (data[key] ?: emptyList()).filter { it.isNotBlank() })
        if (nonBlankValues.isEmpty()) null else encodeListValue(nonBlankValues)
      }
      PropertyType.OBJECT -> {
        val nestedValues = property.nestedProperties.associate { it.id.value to rawPropertyValue(it, data, "$key.") }
        if (nestedValues.values.all { it == null }) null else nestedValues
      }
      else -> (data[key] ?: emptyList()).filter { it.isNotBlank() }.firstOrNull()
    }
  }

  /**
   * Returns violations for Reference property values that do not point to an existing instance of their target entity — or to one of
   * [pendingIds], target objects auto-created by a reference lookup in the same save (ADR 0026).
   */
  private fun referenceViolations(property: Property, storedValue: String?, installedAppId: InstalledAppId, pendingIds: Set<String>): List<PropertyConstraintViolation> {
    if (storedValue.isNullOrBlank()) return emptyList()
    val referencedIds = when {
      property.type == PropertyType.REF -> listOf(storedValue)
      property.type == PropertyType.LIST && property.listItemType == PropertyType.REF -> decodeListValue(storedValue)
      else -> return emptyList()
    }
    val targetEntityId = property.targetEntityId ?: return referencedIds.map { PropertyConstraintViolation.InvalidReferenceViolation }
    val existingTargetIds = appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, targetEntityId).map { it.id.value }
    return referencedIds.filter { it !in existingTargetIds && it !in pendingIds }.map { PropertyConstraintViolation.InvalidReferenceViolation }
  }

  /**
   * For a property with a [Property.unit], parses its raw submitted text via [parseUnitValue] and converts it into a
   * number expressed in [PropertyUnit.storageGranularity] — unlike DURATION, the raw text itself is never persisted.
   * Returns the (possibly converted) value to store alongside any violations (invalid format, or a non-integer result
   * for a LONG property). Values of properties without a unit pass through unchanged.
   */
  private fun unitConvertedValue(property: Property, rawValue: String?): Pair<String?, List<PropertyConstraintViolation>> {
    val unit = property.unit ?: return rawValue to emptyList()
    if (rawValue.isNullOrBlank()) return rawValue to emptyList()
    val parsed = parseUnitValue(rawValue, unit.storageGranularity) ?: return rawValue to listOf(PropertyConstraintViolation.InvalidUnitFormatViolation)
    if (property.type == PropertyType.LONG && parsed.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
      return rawValue to listOf(PropertyConstraintViolation.NonIntegerUnitValueViolation)
    }
    val converted = if (property.type == PropertyType.LONG) parsed.toBigInteger().toString() else parsed.stripTrailingZeros().toPlainString()
    return converted to emptyList()
  }

  private fun parseValue(property: Property, storedValue: String?): Any? {
    if (storedValue.isNullOrBlank()) return null
    return when (property.type) {
      PropertyType.LIST -> if (property.isObjectList) {
        decodeListValue(storedValue).map { parseObjectValue(property, decodeObjectValue(it)) }
      } else {
        val itemType = property.listItemType ?: PropertyType.STRING
        decodeListValue(storedValue).map { parseScalarValue(itemType, it) }
      }
      PropertyType.OBJECT -> parseObjectValue(property, decodeObjectValue(storedValue))
      else -> parseScalarValue(property.type, storedValue)
    }
  }

  /** Parses the raw (already JSON-decoded) values of an OBJECT property's nested properties, recursing into nested OBJECT properties. */
  private fun parseObjectValue(property: Property, raw: Map<String, Any?>): Map<String, Any?> = property.nestedProperties.associate { nested ->
    val rawNestedValue = raw[nested.id.value]
    val parsedValue = if (nested.type == PropertyType.OBJECT && rawNestedValue is Map<*, *>) {
      @Suppress("UNCHECKED_CAST")
      parseObjectValue(nested, rawNestedValue as Map<String, Any?>)
    } else {
      parseValue(nested, rawNestedValue as? String)
    }
    nested.id.value to parsedValue
  }

  private fun parseScalarValue(type: PropertyType, rawValue: String): Any? = when (type) {
    PropertyType.LONG -> rawValue.toLongOrNull()
    PropertyType.DOUBLE -> rawValue.toDoubleOrNull()
    PropertyType.BOOLEAN -> rawValue.equals("true", ignoreCase = true)
    else -> rawValue
  }

  override fun getValueProposals(
    userId: String,
    installedAppId: String,
    entityTypeId: String,
    propertyId: String,
    currentData: Map<String, String>,
  ): Either<DomainError, List<String>> {
    val installedApp = installedAppRepository.findById(InstalledAppId(installedAppId))
    if (installedApp == null || installedApp.userId != userId) {
      logger.warn { "Get value proposals failed: installed app not found: $installedAppId for user: $userId" }
      return AppDataError.INSTALLED_APP_NOT_FOUND.left()
    }

    val appVersion = resolveAppVersion(installedApp)
    if (appVersion == null) {
      logger.warn { "Get value proposals failed: app version not found for installed app: $installedAppId" }
      return AppDataError.INSTALLED_APP_NOT_FOUND.left()
    }

    val entityDef = appVersion.entityDefinitions.find { it.id.value == entityTypeId }
    if (entityDef == null) {
      logger.warn { "Get value proposals failed: entity not found: $entityTypeId in version ${appVersion.id.value}" }
      return AppDataError.ENTITY_NOT_FOUND.left()
    }

    val property = entityDef.properties.find { it.id.value == propertyId }
    if (property == null) {
      logger.warn { "Get value proposals failed: property not found: $propertyId in entity $entityTypeId" }
      return AppDataError.PROPERTY_NOT_FOUND.left()
    }

    if (property.valueProposals.isEmpty()) {
      return emptyList<String>().right()
    }

    val allEntityData = appDataRepository.findAllByInstalledAppIdAndEntityType(
      InstalledAppId(installedAppId),
      EntityDefinitionId(entityTypeId),
    )

    // Filter candidates: only those that match current values for all value-proposal filter properties
    val candidates = allEntityData.filter { item ->
      property.valueProposals.all { filterPropId ->
        val filterValue = currentData[filterPropId]
        if (filterValue.isNullOrBlank()) {
          true
        } else {
          item.data[filterPropId] == filterValue
        }
      }
    }

    // Collect non-null values for the target property, count occurrences, sort by most used
    val valueCounts = candidates
      .mapNotNull { it.data[propertyId] }
      .filter { it.isNotBlank() }
      .groupingBy { it }
      .eachCount()

    val proposals = valueCounts.entries
      .sortedByDescending { it.value }
      .map { it.key }

    return proposals.right()
  }

  private fun resolveAppVersion(installedApp: InstalledApp): AppVersion? =
    resolveInstalledAppVersion(installedApp, appVersionRepository)

  companion object : KLogging()
}

private data class PropertyReference(val referencingData: AppData, val property: Property)
