package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.formatDurationValue
import de.chrgroth.james.platform.domain.model.app.granularityByName
import de.chrgroth.james.platform.domain.model.app.parseDurationValue
import de.chrgroth.james.platform.domain.model.imports.DurationConversionUnit
import de.chrgroth.james.platform.domain.model.imports.FieldMappingConversion
import java.math.BigDecimal
import java.math.MathContext
import java.time.Duration

/** Converts a raw string value (from a source record or a static fallback/lookup value) into the type [de.chrgroth.james.platform.domain.port.`in`.app.PropertyConstraintPort] expects for the given target property type. Returns null for blank input. */
internal fun parseScalarValue(type: PropertyType, rawValue: String?): Any? {
  if (rawValue.isNullOrBlank()) return null
  return when (type) {
    PropertyType.LONG -> rawValue.toLongOrNull()
    PropertyType.DOUBLE -> rawValue.toDoubleOrNull()
    PropertyType.BOOLEAN -> rawValue.equals("true", ignoreCase = true)
    PropertyType.DURATION -> parseDurationValue(rawValue)
    else -> rawValue
  }
}

/**
 * Applies a [FieldMappingConversion] to a raw source value before it is interpreted as the target property type by
 * [parseScalarValue], then - if the target property carries a [unit] - converts it from [importGranularity] (a
 * [de.chrgroth.james.platform.domain.model.app.Granularity] name of the unit's family, e.g. `"KILOMETERS"`) to the
 * unit's `storageGranularity`. Most [FieldMappingConversion]s (e.g. STRING_TO_LONG) only relax type-compatibility
 * checks and do not need any actual value transformation, since [parseScalarValue] already parses purely based on
 * the target property type; DATETIME_TO_DATE and LONG_TO_DURATION are the exception, as their target's expected
 * textual format differs from the raw source value. Returns [rawValue] unchanged for every other conversion
 * (including NONE) and when [unit] is null.
 */
internal fun applyConversion(
  conversion: FieldMappingConversion,
  conversionUnit: DurationConversionUnit?,
  unit: PropertyUnit?,
  importGranularity: String?,
  rawValue: String?,
): String? {
  if (rawValue == null) return null
  val converted = when (conversion) {
    FieldMappingConversion.DATETIME_TO_DATE -> rawValue.substringBefore('T').substringBefore(' ')
    FieldMappingConversion.LONG_TO_DURATION -> rawValue.toLongOrNull()?.let { amount ->
      formatDurationValue(Duration.ofSeconds(amount * (conversionUnit ?: DurationConversionUnit.SECONDS).secondsPerUnit))
    } ?: rawValue
    else -> rawValue
  }
  return applyGranularityConversion(unit, importGranularity, converted)
}

/** Generalizes [DurationConversionUnit]'s fixed-ratio conversion to any [PropertyUnit] family, using each granularity's `factorToSmallestUnit`. */
private fun applyGranularityConversion(unit: PropertyUnit?, importGranularity: String?, rawValue: String): String {
  if (unit == null) return rawValue
  val sourceGranularity = granularityByName(unit.family, importGranularity) ?: return rawValue
  if (sourceGranularity == unit.storageGranularity) return rawValue
  val amount = rawValue.toBigDecimalOrNull() ?: return rawValue
  val converted = amount.multiply(BigDecimal(sourceGranularity.factorToSmallestUnit))
    .divide(BigDecimal(unit.storageGranularity.factorToSmallestUnit), MathContext.DECIMAL64)
  return converted.stripTrailingZeros().toPlainString()
}

private val DurationConversionUnit.secondsPerUnit: Long
  get() = when (this) {
    DurationConversionUnit.SECONDS -> 1L
    DurationConversionUnit.MINUTES -> 60L
    DurationConversionUnit.HOURS -> 3_600L
    DurationConversionUnit.DAYS -> 86_400L
  }
