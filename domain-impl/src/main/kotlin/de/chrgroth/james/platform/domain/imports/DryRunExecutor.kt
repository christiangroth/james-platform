package de.chrgroth.james.platform.domain.imports

import com.fasterxml.jackson.databind.JsonNode
import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyId
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
 * to [FieldMapping.fallbackValue] when no match is found.
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

        if (!property.nullable && rawValue.isNullOrBlank()) {
          issues += DryRunIssue.MissingMandatoryValue(property.id)
        }

        val parsedValue = parseScalarValue(property.type, rawValue)
        val violations = propertyConstraint.checkValue(property, parsedValue, seenValues[property.id].orEmpty())
        issues += violations.map { DryRunIssue.ConstraintViolated(property.id, it, it.javaClass in STATICALLY_CHECKED_VIOLATION_TYPES) }

        if (parsedValue != null) {
          seenValues[property.id]?.add(parsedValue)
        }
      }

      DryRunObject(index, record.toString(), targetData, issues)
    }
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
      sourceNode != null && !sourceNode.isNull && !sourceNode.isMissingNode -> sourceNode.asText()
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
