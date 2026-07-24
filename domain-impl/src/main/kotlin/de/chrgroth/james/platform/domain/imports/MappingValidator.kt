package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.FieldMappingConversion
import de.chrgroth.james.platform.domain.model.imports.Mapping
import de.chrgroth.james.platform.domain.model.imports.MappingIssue
import de.chrgroth.james.platform.domain.model.imports.MappingValidationResult
import de.chrgroth.james.platform.domain.model.imports.ReferenceLookup
import de.chrgroth.james.platform.domain.model.imports.SchemaProperty
import de.chrgroth.james.platform.domain.model.imports.SchemaPropertyType
import de.chrgroth.james.platform.domain.port.`in`.app.PropertyConstraintPort

/**
 * Validates a [Mapping] against its target [EntityDefinition] and the source document's detected schema:
 * every mandatory target property must resolve to a value (mapped source field, [ReferenceLookup], or static
 * fallback), the observed source type(s) must be compatible with the target property type (directly or via the
 * configured conversion), and for numeric/string-length constrained target properties the source schema statistics
 * (min/max, string lengths) must not violate those constraints. A configured fallback value is checked against the
 * target property's constraints via [PropertyConstraintPort]. Pattern constraints cannot be statically checked here
 * and are reported separately as [MappingIssue.NotStaticallyValidated].
 */
object MappingValidator {

  /** Types directly assignable to a target property type without any conversion. */
  private val DIRECT_COMPATIBILITY: Map<SchemaPropertyType, PropertyType> = mapOf(
    SchemaPropertyType.STRING to PropertyType.STRING,
    SchemaPropertyType.DATE to PropertyType.DATE,
    SchemaPropertyType.DATETIME to PropertyType.DATETIME,
    SchemaPropertyType.LONG to PropertyType.LONG,
    SchemaPropertyType.DOUBLE to PropertyType.DOUBLE,
    SchemaPropertyType.BOOLEAN to PropertyType.BOOLEAN,
  )

  /** Source/target type pair each conversion resolves. */
  private val CONVERSIONS: Map<FieldMappingConversion, Pair<SchemaPropertyType, PropertyType>> = mapOf(
    FieldMappingConversion.STRING_TO_LONG to (SchemaPropertyType.STRING to PropertyType.LONG),
    FieldMappingConversion.STRING_TO_DOUBLE to (SchemaPropertyType.STRING to PropertyType.DOUBLE),
    FieldMappingConversion.STRING_TO_BOOLEAN to (SchemaPropertyType.STRING to PropertyType.BOOLEAN),
    FieldMappingConversion.LONG_TO_DOUBLE to (SchemaPropertyType.LONG to PropertyType.DOUBLE),
    FieldMappingConversion.LONG_TO_STRING to (SchemaPropertyType.LONG to PropertyType.STRING),
    FieldMappingConversion.DOUBLE_TO_STRING to (SchemaPropertyType.DOUBLE to PropertyType.STRING),
    FieldMappingConversion.BOOLEAN_TO_STRING to (SchemaPropertyType.BOOLEAN to PropertyType.STRING),
    FieldMappingConversion.STRING_TO_DATE to (SchemaPropertyType.STRING to PropertyType.DATE),
    FieldMappingConversion.STRING_TO_DATETIME to (SchemaPropertyType.STRING to PropertyType.DATETIME),
  )

  fun validate(
    mapping: Mapping,
    entityDefinition: EntityDefinition,
    schema: List<SchemaProperty>,
    entityDefinitions: List<EntityDefinition>,
    propertyConstraint: PropertyConstraintPort,
  ): MappingValidationResult {
    val schemaByPath = schema.associateBy { it.path }
    val fieldMappingsByTarget = mapping.fieldMappings.associateBy { it.targetPropertyId }
    val entityDefinitionsById = entityDefinitions.associateBy { it.id }
    val issues = mutableListOf<MappingIssue>()

    for (property in entityDefinition.properties) {
      val fieldMapping = fieldMappingsByTarget[property.id]
      issues += validateProperty(property, fieldMapping, schemaByPath, entityDefinitionsById, propertyConstraint)
    }

    return MappingValidationResult(issues)
  }

  private fun validateProperty(
    property: Property,
    fieldMapping: FieldMapping?,
    schemaByPath: Map<String, SchemaProperty>,
    entityDefinitionsById: Map<EntityDefinitionId, EntityDefinition>,
    propertyConstraint: PropertyConstraintPort,
  ): List<MappingIssue> {
    val issues = mutableListOf<MappingIssue>()
    val hasFallback = !fieldMapping?.fallbackValue.isNullOrBlank()
    val referenceLookup = fieldMapping?.referenceLookup

    if (property.type == PropertyType.REF && referenceLookup != null) {
      issues += validateReferenceLookup(property, referenceLookup, schemaByPath, entityDefinitionsById)
      // Whether a configured lookup actually finds a match depends on the referenced entity's persisted data, which
      // is unknowable here; only an empty (thus certain-to-never-match) criteria list is treated like "unmapped".
      if (referenceLookup.criteria.isEmpty() && !property.nullable && !hasFallback) {
        issues += MappingIssue.MissingMandatoryField(property.id)
      }
      issues += validateFallback(property, fieldMapping, propertyConstraint)
      return issues
    }

    val sourcePath = fieldMapping?.sourcePath
    val sourceProperty = sourcePath?.let { schemaByPath[it] }

    if (sourcePath == null) {
      if (!property.nullable && !hasFallback) issues += MappingIssue.MissingMandatoryField(property.id)
      issues += validateFallback(property, fieldMapping, propertyConstraint)
      return issues
    }
    if (sourceProperty == null) {
      // Mapped source path no longer exists in the (re-detected) schema; treat like unmapped.
      if (!property.nullable && !hasFallback) issues += MappingIssue.MissingMandatoryField(property.id)
      issues += validateFallback(property, fieldMapping, propertyConstraint)
      return issues
    }

    if (!property.nullable && !sourceProperty.mandatory && !hasFallback) {
      issues += MappingIssue.MissingMandatoryField(property.id)
    }

    val conversion = fieldMapping.conversion
    val observedTypes = sourceProperty.typeCounts.keys.filterNot { it == SchemaPropertyType.NULL }
    val incompatibleType = observedTypes.firstOrNull { !isCompatible(it, property.type, conversion) }
    if (incompatibleType != null) {
      issues += MappingIssue.IncompatibleType(property.id, incompatibleType, property.type)
    } else {
      checkNumericRange(property, sourceProperty)?.let { issues += it }
      checkStringLength(property, sourceProperty)?.let { issues += it }
    }

    property.constraints.filterIsInstance<PropertyConstraint.Pattern>().forEach {
      issues += MappingIssue.NotStaticallyValidated(property.id, it.regex)
    }

    issues += validateFallback(property, fieldMapping, propertyConstraint)

    return issues
  }

  /** Validates that every criterion targets an existing property of the referenced entity definition and that the mapped source field's observed type(s) are compatible with it. */
  private fun validateReferenceLookup(
    property: Property,
    referenceLookup: ReferenceLookup,
    schemaByPath: Map<String, SchemaProperty>,
    entityDefinitionsById: Map<EntityDefinitionId, EntityDefinition>,
  ): List<MappingIssue> {
    if (referenceLookup.criteria.isEmpty()) return listOf(MappingIssue.ReferenceLookupMissingCriteria(property.id))
    val referencedEntity = property.targetEntityId?.let { entityDefinitionsById[it] }
      ?: return listOf(MappingIssue.ReferenceLookupMissingCriteria(property.id))
    val referencedPropertiesById = referencedEntity.properties.associateBy { it.id }

    val issues = mutableListOf<MappingIssue>()
    for (criterion in referenceLookup.criteria) {
      val referencedProperty = referencedPropertiesById[criterion.targetPropertyId]
      if (referencedProperty == null) {
        issues += MappingIssue.ReferenceLookupInvalidCriterion(property.id, criterion.targetPropertyId)
        continue
      }
      val sourceProperty = schemaByPath[criterion.sourcePath] ?: continue
      val observedTypes = sourceProperty.typeCounts.keys.filterNot { it == SchemaPropertyType.NULL }
      val incompatibleType = observedTypes.firstOrNull { !isCompatible(it, referencedProperty.type, FieldMappingConversion.NONE) }
      if (incompatibleType != null) {
        issues += MappingIssue.ReferenceLookupIncompatibleType(property.id, criterion.targetPropertyId, incompatibleType, referencedProperty.type)
      }
    }
    return issues
  }

  /** Pattern/regex constraints on the fallback value are not statically checked here (analogous to [MappingIssue.NotStaticallyValidated] for source-mapped fields) and are only validated during the dry-run. */
  private fun validateFallback(property: Property, fieldMapping: FieldMapping?, propertyConstraint: PropertyConstraintPort): List<MappingIssue> {
    val fallbackValue = fieldMapping?.fallbackValue?.takeIf { it.isNotBlank() } ?: return emptyList()
    val parsedValue = parseScalarValue(property.type, fallbackValue)
    return propertyConstraint.checkValue(property, parsedValue)
      .filterNot { it is PropertyConstraintViolation.PatternViolation }
      .map { MappingIssue.FallbackValueViolatesConstraint(property.id, it) }
  }

  private fun isCompatible(sourceType: SchemaPropertyType, targetType: PropertyType, conversion: FieldMappingConversion): Boolean =
    if (conversion == FieldMappingConversion.NONE) {
      DIRECT_COMPATIBILITY[sourceType] == targetType
    } else {
      CONVERSIONS[conversion] == (sourceType to targetType)
    }

  private fun checkNumericRange(property: Property, sourceProperty: SchemaProperty): MappingIssue.NumericRangeViolation? {
    val range = sourceProperty.numericRange ?: return null
    val min = property.constraints.filterIsInstance<PropertyConstraint.MinLong>().firstOrNull()?.min?.toDouble()
      ?: property.constraints.filterIsInstance<PropertyConstraint.MinDouble>().firstOrNull()?.min
    val max = property.constraints.filterIsInstance<PropertyConstraint.MaxLong>().firstOrNull()?.max?.toDouble()
      ?: property.constraints.filterIsInstance<PropertyConstraint.MaxDouble>().firstOrNull()?.max
    if (min == null && max == null) return null
    val violated = (min != null && range.min < min) || (max != null && range.max > max)
    return if (violated) MappingIssue.NumericRangeViolation(property.id, min, max, range.min, range.max) else null
  }

  private fun checkStringLength(property: Property, sourceProperty: SchemaProperty): MappingIssue.StringLengthViolation? {
    if (sourceProperty.stringLengthCounts.isEmpty()) return null
    val min = property.constraints.filterIsInstance<PropertyConstraint.MinLength>().firstOrNull()?.min
    val max = property.constraints.filterIsInstance<PropertyConstraint.MaxLength>().firstOrNull()?.max
    if (min == null && max == null) return null
    val observedMin = sourceProperty.stringLengthCounts.keys.min()
    val observedMax = sourceProperty.stringLengthCounts.keys.max()
    val violated = (min != null && observedMin < min) || (max != null && observedMax > max)
    return if (violated) MappingIssue.StringLengthViolation(property.id, min, max, observedMin, observedMax) else null
  }
}
