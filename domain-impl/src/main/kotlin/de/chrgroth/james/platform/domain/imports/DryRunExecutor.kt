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
import de.chrgroth.james.platform.domain.model.imports.DryRunIssue
import de.chrgroth.james.platform.domain.model.imports.DryRunObject
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.Mapping
import de.chrgroth.james.platform.domain.model.imports.ReferenceLookup
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
    val uniqueKeyProperties = entityDefinition.properties.filter { it.constraints.contains(PropertyConstraint.UniqueKey) }
    val seenValues: Map<PropertyId, MutableList<Any?>> = uniqueKeyProperties.associate { property ->
      property.id to existingAppData.mapNotNullTo(mutableListOf()) { appData ->
        appData.data[property.id.value]?.let { parseScalarValue(property.type, it) }
      }
    }

    return records.mapIndexed { index, record ->
      val targetData = mutableMapOf<PropertyId, String?>()
      val issues = mutableListOf<DryRunIssue>()

      for (property in entityDefinition.properties) {
        val fieldMapping = fieldMappingsByTarget[property.id]
        val rawValue = resolveRawValue(record, property, fieldMapping, entityDefinitionsById, referencedAppDataByEntityId)
        targetData[property.id] = rawValue
        val isUniqueKey = property.constraints.contains(PropertyConstraint.UniqueKey)

        if (!property.nullable && rawValue.isNullOrBlank()) {
          issues += DryRunIssue.MissingMandatoryValue(property.id, expected = isUniqueKey)
        }

        if (property.type == PropertyType.REF && fieldMapping?.referenceLookup == null) {
          referenceExistenceIssue(property, rawValue, referencedAppDataByEntityId)?.let { issues += it }
        }

        val parsedValue = parseScalarValue(property.type, rawValue)
        val violations = propertyConstraint.checkValue(property, parsedValue, seenValues[property.id].orEmpty())
        issues += violations.map {
          DryRunIssue.ConstraintViolated(
            property.id,
            it,
            staticallyChecked = it.javaClass in STATICALLY_CHECKED_VIOLATION_TYPES,
            expected = it is PropertyConstraintViolation.UniqueKeyViolation,
          )
        }

        if (parsedValue != null) {
          seenValues[property.id]?.add(parsedValue)
        }
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
    return when {
      sourceNode != null && !sourceNode.isNull && !sourceNode.isMissingNode ->
        applyConversion(fieldMapping.conversion, fieldMapping.conversionUnit, sourceNode.asText())
      !fieldMapping?.fallbackValue.isNullOrBlank() -> fieldMapping.fallbackValue
      else -> null
    }
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
