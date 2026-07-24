package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.FieldMappingConversion
import de.chrgroth.james.platform.domain.model.imports.Mapping
import de.chrgroth.james.platform.domain.model.imports.MappingIssue
import de.chrgroth.james.platform.domain.model.imports.MappingValidationResult
import de.chrgroth.james.platform.domain.model.imports.SchemaProperty
import de.chrgroth.james.platform.domain.model.imports.SchemaPropertyType

/**
 * Validates a [Mapping] against its target [EntityDefinition] and the source document's detected schema:
 * every mandatory target property must resolve to a value (mapped source field or static fallback), the observed
 * source type(s) must be compatible with the target property type (directly or via the configured conversion), and
 * for numeric/string-length constrained target properties the source schema statistics (min/max, string lengths)
 * must not violate those constraints. Pattern constraints cannot be statically checked here and are reported
 * separately as [MappingIssue.NotStaticallyValidated].
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

  fun validate(mapping: Mapping, entityDefinition: EntityDefinition, schema: List<SchemaProperty>): MappingValidationResult {
    val schemaByPath = schema.associateBy { it.path }
    val fieldMappingsByTarget = mapping.fieldMappings.associateBy { it.targetPropertyId }
    val issues = mutableListOf<MappingIssue>()

    for (property in entityDefinition.properties) {
      val fieldMapping = fieldMappingsByTarget[property.id]
      issues += validateProperty(property, fieldMapping, schemaByPath)
    }

    return MappingValidationResult(issues)
  }

  private fun validateProperty(property: Property, fieldMapping: FieldMapping?, schemaByPath: Map<String, SchemaProperty>): List<MappingIssue> {
    val issues = mutableListOf<MappingIssue>()
    val hasFallback = !fieldMapping?.fallbackValue.isNullOrBlank()
    val sourcePath = fieldMapping?.sourcePath
    val sourceProperty = sourcePath?.let { schemaByPath[it] }

    if (sourcePath == null) {
      if (!property.nullable && !hasFallback) issues += MappingIssue.MissingMandatoryField(property.id)
      return issues
    }
    if (sourceProperty == null) {
      // Mapped source path no longer exists in the (re-detected) schema; treat like unmapped.
      if (!property.nullable && !hasFallback) issues += MappingIssue.MissingMandatoryField(property.id)
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

    return issues
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
