package de.chrgroth.james.platform.domain.imports

import arrow.core.getOrElse
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.ValueConversion
import de.chrgroth.james.platform.domain.model.app.granularityByName
import de.chrgroth.james.platform.domain.model.imports.FieldMappingConversion

/** Converts a raw string value (from a source record or a static fallback/lookup value) into the type [de.chrgroth.james.platform.domain.port.`in`.app.PropertyConstraintPort] expects for the given target property type. Returns null for blank input. */
internal fun parseScalarValue(type: PropertyType, rawValue: String?): Any? {
  if (rawValue.isNullOrBlank()) return null
  return when (type) {
    PropertyType.LONG -> rawValue.toLongOrNull()
    PropertyType.DOUBLE -> rawValue.toDoubleOrNull()
    PropertyType.BOOLEAN -> rawValue.equals("true", ignoreCase = true)
    else -> rawValue
  }
}

/**
 * Applies a [FieldMappingConversion] to a raw source value via [ValueConversion.convert], then - if the target
 * property carries a [unit] - converts it from [importGranularity] (a
 * [de.chrgroth.james.platform.domain.model.app.Granularity] name of the unit's family, e.g. `"KILOMETERS"`) to the
 * unit's `storageGranularity` via [ValueConversion.convertGranularity]. Falls back to [rawValue] unchanged if
 * [conversion] is [FieldMappingConversion.NONE] or the conversion fails (an incompatible/unparseable source value is
 * instead reported downstream, either statically by `MappingValidator` or per-record by `DryRunExecutor`'s constraint
 * checks). Returns null for a null [rawValue] and when [unit] is null skips the granularity step.
 */
internal fun applyConversion(
  conversion: FieldMappingConversion,
  unit: PropertyUnit?,
  importGranularity: String?,
  rawValue: String?,
): String? {
  if (rawValue == null) return null
  val sourceType = conversion.sourceType
  val targetType = conversion.targetType
  val converted = if (sourceType != null && targetType != null) {
    ValueConversion.convert(sourceType, targetType, rawValue).getOrElse { rawValue } ?: rawValue
  } else {
    rawValue
  }
  return applyGranularityConversion(unit, importGranularity, converted)
}

/** Converts [rawValue] from [importGranularity] to a [PropertyUnit]'s `storageGranularity`. */
private fun applyGranularityConversion(unit: PropertyUnit?, importGranularity: String?, rawValue: String): String {
  if (unit == null) return rawValue
  val sourceGranularity = granularityByName(unit.family, importGranularity) ?: return rawValue
  return ValueConversion.convertGranularity(unit, sourceGranularity, rawValue)
}
