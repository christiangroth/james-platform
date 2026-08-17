package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.granularityByName
import de.chrgroth.james.platform.domain.model.imports.FieldMappingConversion
import java.math.BigDecimal
import java.math.MathContext

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
 * Applies a [FieldMappingConversion] to a raw source value before it is interpreted as the target property type by
 * [parseScalarValue], then - if the target property carries a [unit] - converts it from [importGranularity] (a
 * [de.chrgroth.james.platform.domain.model.app.Granularity] name of the unit's family, e.g. `"KILOMETERS"`) to the
 * unit's `storageGranularity`. Most [FieldMappingConversion]s (e.g. STRING_TO_LONG) only relax type-compatibility
 * checks and do not need any actual value transformation, since [parseScalarValue] already parses purely based on
 * the target property type; DATETIME_TO_DATE is the exception, as its target's expected textual format differs from
 * the raw source value. Returns [rawValue] unchanged for every other conversion (including NONE) and when [unit] is null.
 */
internal fun applyConversion(
  conversion: FieldMappingConversion,
  unit: PropertyUnit?,
  importGranularity: String?,
  rawValue: String?,
): String? {
  if (rawValue == null) return null
  val converted = when (conversion) {
    FieldMappingConversion.DATETIME_TO_DATE -> rawValue.substringBefore('T').substringBefore(' ')
    else -> rawValue
  }
  return applyGranularityConversion(unit, importGranularity, converted)
}

/** Converts [rawValue] from [importGranularity] to a [PropertyUnit]'s `storageGranularity`, using each granularity's `factorToSmallestUnit`. */
private fun applyGranularityConversion(unit: PropertyUnit?, importGranularity: String?, rawValue: String): String {
  if (unit == null) return rawValue
  val sourceGranularity = granularityByName(unit.family, importGranularity) ?: return rawValue
  if (sourceGranularity == unit.storageGranularity) return rawValue
  val amount = rawValue.toBigDecimalOrNull() ?: return rawValue
  val converted = amount.multiply(BigDecimal(sourceGranularity.factorToSmallestUnit))
    .divide(BigDecimal(unit.storageGranularity.factorToSmallestUnit), MathContext.DECIMAL64)
  return converted.stripTrailingZeros().toPlainString()
}
