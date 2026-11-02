package de.chrgroth.james.platform.domain.imports

import com.fasterxml.jackson.databind.JsonNode
import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.encodeObjectValue
import de.chrgroth.james.platform.domain.model.app.uniqueKeyComparisonValues
import de.chrgroth.james.platform.domain.model.app.uniqueKeyProperties
import de.chrgroth.james.platform.domain.model.imports.DryRunIssue
import de.chrgroth.james.platform.domain.model.imports.DryRunObject
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.ImportTarget
import de.chrgroth.james.platform.domain.model.imports.Mapping
import de.chrgroth.james.platform.domain.model.imports.ReferenceLookup
import de.chrgroth.james.platform.domain.model.imports.importTargets
import de.chrgroth.james.platform.domain.port.`in`.app.PropertyConstraintPort

/**
 * Builds and validates every target object a [Mapping] would produce from the source records at the import
 * document's selected data path, without saving anything. Reuses [PropertyConstraintPort] (the same validator used
 * when actually creating an `AppData` instance) so a dry-run finding is guaranteed to match what would happen on
 * acceptance. Constraint categories already covered statically by [MappingValidator] (missing mandatory field,
 * min/max value, min/max length) are flagged as such; everything else (in particular Pattern/regex, which
 * [de.chrgroth.james.platform.domain.model.imports.MappingIssue.NotStaticallyValidated] already deferred to here) is new.
 * A REF property with a [ReferenceLookup] resolves its value by matching the lookup criteria's source values
 * against [referencedAppDataByEntityId] for the referenced entity (a `find`, never creating anything), falling back
 * to [FieldMapping.fallbackValue] when no match is found. A REF property mapped directly (no lookup, via
 * [FieldMapping.sourcePath] and/or [FieldMapping.fallbackValue]) has its resolved value checked against
 * [referencedAppDataByEntityId] as well, reporting [PropertyConstraintViolation.InvalidReferenceViolation] when it
 * does not point to an existing instance of the referenced entity — a lookup-resolved value is inherently
 * existence-safe and skips this check.
 *
 * Properties nested in OBJECT properties are mapped individually (see [ImportTarget]); an OBJECT property's value is
 * the JSON object assembled from its nested values (as stored by the app data forms), or empty when none of them has
 * a value. A nested property's issues carry its own id; a mandatory nested property is only required when its
 * enclosing object has a value or is itself (transitively) mandatory.
 */
object DryRunExecutor {

  private const val PATH_SEPARATOR = "."

  private val STATICALLY_CHECKED_VIOLATION_TYPES: Set<Class<out PropertyConstraintViolation>> = setOf(
    PropertyConstraintViolation.MinValueViolation::class.java,
    PropertyConstraintViolation.MaxValueViolation::class.java,
    PropertyConstraintViolation.MinLengthViolation::class.java,
    PropertyConstraintViolation.MaxLengthViolation::class.java,
  )

  fun execute(
    records: List<JsonNode>,
    mapping: Mapping,
    entityDefinition: EntityDefinition,
    existingAppData: List<AppData>,
    entityDefinitionsById: Map<EntityDefinitionId, EntityDefinition>,
    referencedAppDataByEntityId: Map<EntityDefinitionId, List<AppData>>,
    propertyConstraint: PropertyConstraintPort,
  ): List<DryRunObject> {
    val fieldMappingsByTarget = mapping.fieldMappings.associateBy { it.targetPropertyId }
    // all UniqueKey properties together form one composite key (see uniqueKeyProperties); seenKeys holds the key of every persisted or to-be-saved object
    val keyProperties = entityDefinition.uniqueKeyProperties
    val seenKeys: MutableList<Map<PropertyId, Any?>> = existingAppData.mapTo(mutableListOf()) { appData ->
      keyProperties.associate { it.id to appData.data[it.id.value]?.let { v -> parseScalarValue(it.type, v) } }
    }

    val nestedTargets = entityDefinition.importTargets().filter { it.parents.isNotEmpty() }

    return records.mapIndexed { index, record ->
      val targetData = mutableMapOf<PropertyId, String?>()
      val nestedValues = mutableMapOf<PropertyId, String?>()
      val issues = mutableListOf<DryRunIssue>()
      for (target in nestedTargets) {
        nestedValues[target.property.id] =
          resolveRawValue(record, target.property, fieldMappingsByTarget[target.property.id], entityDefinitionsById, referencedAppDataByEntityId)
      }
      for (property in entityDefinition.properties) {
        targetData[property.id] = if (property.type == PropertyType.OBJECT) {
          objectValue(property, nestedValues)?.let { encodeObjectValue(it) }
        } else {
          resolveRawValue(record, property, fieldMappingsByTarget[property.id], entityDefinitionsById, referencedAppDataByEntityId)
        }
      }
      val keyOfRecord = keyProperties.associate { it.id to parseScalarValue(it.type, targetData[it.id]) }

      for (property in entityDefinition.properties) {
        val fieldMapping = fieldMappingsByTarget[property.id]
        val rawValue = targetData[property.id]
        val isUniqueKey = property.constraints.contains(PropertyConstraint.UniqueKey)

        if (!property.nullable && rawValue.isNullOrBlank()) {
          issues += DryRunIssue.MissingMandatoryValue(property.id, expected = isUniqueKey)
        }

        if (property.type == PropertyType.REF && fieldMapping?.referenceLookup == null) {
          referenceExistenceIssue(property, rawValue, referencedAppDataByEntityId)?.let { issues += it }
        }

        val parsedValue = parseScalarValue(property.type, rawValue)
        // a decimal source scaled into a LONG unit property (e.g. 19.75 km into meters) can still end up fractional
        if (property.type == PropertyType.LONG && property.unit != null && !rawValue.isNullOrBlank() && parsedValue == null) {
          issues += DryRunIssue.ConstraintViolated(property.id, PropertyConstraintViolation.NonIntegerUnitValueViolation, staticallyChecked = false)
        }
        val violations = propertyConstraint.checkValue(property, parsedValue, uniqueKeyComparisonValues(property, keyProperties, keyOfRecord, seenKeys))
        issues += violations.map {
          DryRunIssue.ConstraintViolated(
            property.id,
            it,
            staticallyChecked = it.javaClass in STATICALLY_CHECKED_VIOLATION_TYPES,
            expected = it is PropertyConstraintViolation.UniqueKeyViolation,
          )
        }
      }

      for (target in nestedTargets) {
        val objectPresent = targetData[target.topLevelProperty.id] != null
        issues += nestedValueIssues(target, nestedValues[target.property.id], objectPresent, fieldMappingsByTarget[target.property.id], referencedAppDataByEntityId, propertyConstraint)
      }

      // Only a record that is actually saved on accept may claim its key: an invalid or skipped record is discarded, so
      // letting it reserve the key would wrongly discard a later, valid record carrying the same key.
      if (issues.isEmpty() && keyProperties.isNotEmpty()) {
        seenKeys += keyOfRecord
      }

      DryRunObject(index, record.toString(), targetData, issues)
    }
  }

  /**
   * Runs [execute] over a single [record] - the Mapping step's live preview of the *currently edited*, not yet
   * saved, [mapping]. Since this is a batch of one, [execute]'s in-batch `UniqueKey` fan-in tracking (`seenValues`)
   * has nothing else in the batch to compare against - only a collision with [existingAppData] can be detected
   * here, not one with another record of the import job. That trade-off is accepted for a single-object preview;
   * the full picture, across every record, is still what the Dry-Run page shows via [execute].
   */
  fun executeSingle(
    record: JsonNode,
    mapping: Mapping,
    entityDefinition: EntityDefinition,
    existingAppData: List<AppData>,
    entityDefinitionsById: Map<EntityDefinitionId, EntityDefinition>,
    referencedAppDataByEntityId: Map<EntityDefinitionId, List<AppData>>,
    propertyConstraint: PropertyConstraintPort,
  ): DryRunObject =
    execute(listOf(record), mapping, entityDefinition, existingAppData, entityDefinitionsById, referencedAppDataByEntityId, propertyConstraint).single()

  /** Assembles an OBJECT property's value from the resolved [nestedValues] (recursing into nested OBJECT properties), or null when no nested value is present. */
  private fun objectValue(property: Property, nestedValues: Map<PropertyId, String?>): Map<String, Any?>? =
    property.nestedProperties
      .associate { nested -> nested.id.value to if (nested.type == PropertyType.OBJECT) objectValue(nested, nestedValues) else nestedValues[nested.id] }
      .takeIf { values -> values.values.any { it != null } }

  /** Validates one nested property's value like a top-level one, except that uniqueness does not apply and a missing value only counts when the object is present or mandatory. */
  @Suppress("LongParameterList")
  private fun nestedValueIssues(
    target: ImportTarget,
    rawValue: String?,
    objectPresent: Boolean,
    fieldMapping: FieldMapping?,
    referencedAppDataByEntityId: Map<EntityDefinitionId, List<AppData>>,
    propertyConstraint: PropertyConstraintPort,
  ): List<DryRunIssue> {
    val property = target.property
    val issues = mutableListOf<DryRunIssue>()
    if (!property.nullable && rawValue.isNullOrBlank() && (objectPresent || target.effectivelyMandatory)) {
      issues += DryRunIssue.MissingMandatoryValue(property.id)
    }
    if (property.type == PropertyType.REF && fieldMapping?.referenceLookup == null) {
      referenceExistenceIssue(property, rawValue, referencedAppDataByEntityId)?.let { issues += it }
    }
    val parsedValue = parseScalarValue(property.type, rawValue)
    if (property.type == PropertyType.LONG && property.unit != null && !rawValue.isNullOrBlank() && parsedValue == null) {
      issues += DryRunIssue.ConstraintViolated(property.id, PropertyConstraintViolation.NonIntegerUnitValueViolation, staticallyChecked = false)
    }
    issues += propertyConstraint.checkValue(property, parsedValue, emptyList()).map {
      DryRunIssue.ConstraintViolated(property.id, it, staticallyChecked = it.javaClass in STATICALLY_CHECKED_VIOLATION_TYPES)
    }
    return issues
  }

  /** For a directly mapped REF property (no lookup), reports [PropertyConstraintViolation.InvalidReferenceViolation] if the resolved value does not point to an existing instance of the referenced entity. */
  private fun referenceExistenceIssue(
    property: Property,
    rawValue: String?,
    referencedAppDataByEntityId: Map<EntityDefinitionId, List<AppData>>,
  ): DryRunIssue.ConstraintViolated? {
    if (rawValue.isNullOrBlank()) return null
    val targetEntityId = property.targetEntityId
      ?: return DryRunIssue.ConstraintViolated(property.id, PropertyConstraintViolation.InvalidReferenceViolation, staticallyChecked = false)
    val existingIds = referencedAppDataByEntityId[targetEntityId].orEmpty().map { it.id.value }
    return if (rawValue in existingIds) null else DryRunIssue.ConstraintViolated(property.id, PropertyConstraintViolation.InvalidReferenceViolation, staticallyChecked = false)
  }

  private fun resolveRawValue(
    record: JsonNode,
    property: Property,
    fieldMapping: FieldMapping?,
    entityDefinitionsById: Map<EntityDefinitionId, EntityDefinition>,
    referencedAppDataByEntityId: Map<EntityDefinitionId, List<AppData>>,
  ): String? {
    val referenceLookup = fieldMapping?.referenceLookup
    if (referenceLookup != null) {
      val found = resolveReferenceLookup(record, property, referenceLookup, entityDefinitionsById, referencedAppDataByEntityId)
      return found ?: fieldMapping.fallbackValue?.takeIf { it.isNotBlank() }
    }
    val sourceNode = fieldMapping?.sourcePath?.let { resolvePath(record, it) }
    val sourceValue = if (sourceNode != null && !sourceNode.isNull && !sourceNode.isMissingNode) {
      applyConversion(fieldMapping.conversion, property.type, property.unit, fieldMapping.importGranularity, sourceNode.asText())
        ?.takeUnless { fieldMapping.zeroAsEmpty && isNumericZero(property.type, it) }
    } else {
      null
    }
    return sourceValue ?: fieldMapping?.fallbackValue?.takeIf { it.isNotBlank() }
  }

  /** Performs the `find`: matches every criterion's source value against the referenced entity's corresponding property value, returning the id of the first matching [AppData], or null if none matches. */
  private fun resolveReferenceLookup(
    record: JsonNode,
    property: Property,
    referenceLookup: ReferenceLookup,
    entityDefinitionsById: Map<EntityDefinitionId, EntityDefinition>,
    referencedAppDataByEntityId: Map<EntityDefinitionId, List<AppData>>,
  ): String? {
    if (referenceLookup.criteria.isEmpty()) return null
    val referencedEntityId = property.targetEntityId ?: return null
    val referencedEntity = entityDefinitionsById[referencedEntityId] ?: return null
    val referencedPropertiesById = referencedEntity.properties.associateBy { it.id }

    val expectedValues = mutableListOf<Pair<Property, Any?>>()
    for (criterion in referenceLookup.criteria) {
      val referencedProperty = referencedPropertiesById[criterion.targetPropertyId] ?: return null
      val sourceNode = resolvePath(record, criterion.sourcePath)
      val rawValue = if (sourceNode != null && !sourceNode.isNull && !sourceNode.isMissingNode) sourceNode.asText() else null
      val parsedValue = parseScalarValue(referencedProperty.type, rawValue) ?: return null
      expectedValues += referencedProperty to parsedValue
    }

    val candidates = referencedAppDataByEntityId[referencedEntityId].orEmpty()
    return candidates.firstOrNull { candidate ->
      expectedValues.all { (referencedProperty, expected) -> parseScalarValue(referencedProperty.type, candidate.data[referencedProperty.id.value]) == expected }
    }?.id?.value
  }

  private fun resolvePath(record: JsonNode, path: String): JsonNode? {
    var current: JsonNode = record
    for (segment in path.split(PATH_SEPARATOR)) {
      current = current.get(segment) ?: return null
    }
    return current
  }
}
