package de.chrgroth.james.platform.domain.model.imports

import de.chrgroth.james.platform.domain.model.app.PropertyId

/** Simple, lossless-intent conversions that may be applied to a mapped field to resolve small type mismatches between source and target. */
enum class FieldMappingConversion {
  NONE,
  STRING_TO_LONG,
  STRING_TO_DOUBLE,
  STRING_TO_BOOLEAN,
  LONG_TO_DOUBLE,
  LONG_TO_STRING,
  DOUBLE_TO_STRING,
  BOOLEAN_TO_STRING,
  STRING_TO_DATE,
  STRING_TO_DATETIME,
  DATETIME_TO_DATE,
  LONG_TO_DURATION,
}

/** Unit a raw integer value is interpreted in when applying [FieldMappingConversion.LONG_TO_DURATION]. */
enum class DurationConversionUnit {
  SECONDS,
  MINUTES,
  HOURS,
  DAYS,
}

/**
 * Maps a single source property (identified by [sourcePath], a path into the referenced entity's detected schema)
 * to a search criterion field ([targetPropertyId]) of the entity referenced by a REF target property.
 */
data class ReferenceLookupCriterion(
  val targetPropertyId: PropertyId,
  val sourcePath: String,
)

/**
 * Configures a `find` lookup against the entity definition referenced by a REF target property: every criterion's
 * source value must equal the referenced entity's corresponding property value for a record to match. Deliberately
 * has no `findOrCreate` equivalent, so a lookup never creates a referenced entity as a side effect. Lookups are
 * independent per target property: they cannot access values produced by other field mappings or lookups, so no
 * cycle detection is needed.
 */
data class ReferenceLookup(
  val criteria: List<ReferenceLookupCriterion> = emptyList(),
)

/**
 * Maps a single target property of an [de.chrgroth.james.platform.domain.model.app.EntityDefinition] to a source
 * schema field. Either [sourcePath] (a path into the detected schema), [fallbackValue] (a static value used for
 * every record), [referenceLookup] (only for REF properties; takes priority over [sourcePath] when set), or any
 * combination may be set; a property with none of them is considered unmapped. [conversionUnit] only applies to
 * [FieldMappingConversion.LONG_TO_DURATION] and is ignored otherwise.
 */
data class FieldMapping(
  val targetPropertyId: PropertyId,
  val sourcePath: String? = null,
  val conversion: FieldMappingConversion = FieldMappingConversion.NONE,
  val conversionUnit: DurationConversionUnit? = null,
  val fallbackValue: String? = null,
  val referenceLookup: ReferenceLookup? = null,
)

data class Mapping(
  val fieldMappings: List<FieldMapping> = emptyList(),
)
