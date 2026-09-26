package de.chrgroth.james.platform.domain.model.app

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.math.BigDecimal
import java.math.MathContext

/** Reason a [ValueConversion.convert] call did not produce a value. */
enum class ValueConversionError {
  /** No conversion from the requested source [PropertyType] to the requested target [PropertyType] is supported. */
  UNSUPPORTED_CONVERSION,

  /** The raw value is not in a format the source [PropertyType] can be parsed from. */
  INVALID_VALUE,
}

/**
 * Neutral, [PropertyType]-based value conversion: answers which type pairs are convertible ([isConvertible]),
 * converts a value already in one [PropertyType]'s storage format into another's ([convert]), and converts a unit
 * value between two [Granularity] steps of the same family ([convertGranularity]). Used by the import mapping
 * pipeline (see [de.chrgroth.james.platform.domain.model.imports.FieldMappingConversion]) and intended to be reused
 * by the other data-migration building blocks. Every value [convert] returns is guaranteed to already be in the
 * target type's storage format (e.g. `"true"`/`"false"` for BOOLEAN), so it can be persisted directly without any
 * further transformation.
 */
object ValueConversion {

  /** Every directly supported (source, target) [PropertyType] pair; [isConvertible] and [convert] are both driven by it. */
  private val CONVERTIBLE_PAIRS: Set<Pair<PropertyType, PropertyType>> = setOf(
    PropertyType.STRING to PropertyType.LONG,
    PropertyType.STRING to PropertyType.DOUBLE,
    PropertyType.STRING to PropertyType.BOOLEAN,
    PropertyType.LONG to PropertyType.DOUBLE,
    PropertyType.LONG to PropertyType.STRING,
    PropertyType.DOUBLE to PropertyType.STRING,
    PropertyType.BOOLEAN to PropertyType.STRING,
    PropertyType.STRING to PropertyType.DATE,
    PropertyType.STRING to PropertyType.DATETIME,
    PropertyType.DATETIME to PropertyType.DATE,
  )

  /** Whether a value of [from] can be converted to [to], either because they are the same type or via a supported conversion. */
  fun isConvertible(from: PropertyType, to: PropertyType): Boolean = from == to || (from to to) in CONVERTIBLE_PAIRS

  /**
   * Converts [rawValue] - already in [from]'s storage format - into [to]'s storage format. Returns a blank/null
   * [rawValue] unchanged. [ValueConversionError.UNSUPPORTED_CONVERSION] if [isConvertible] is false for [from]/[to],
   * [ValueConversionError.INVALID_VALUE] if [rawValue] cannot be parsed as [from].
   */
  fun convert(from: PropertyType, to: PropertyType, rawValue: String?): Either<ValueConversionError, String?> {
    if (rawValue.isNullOrBlank()) return rawValue.right()
    if (from == to) return rawValue.right()
    if ((from to to) !in CONVERTIBLE_PAIRS) return ValueConversionError.UNSUPPORTED_CONVERSION.left()
    return when (to) {
      PropertyType.LONG -> rawValue.toLongOrNull()?.toString()?.right() ?: ValueConversionError.INVALID_VALUE.left()
      PropertyType.DOUBLE -> rawValue.toBigDecimalOrNull()?.let { formatDouble(it) }?.right() ?: ValueConversionError.INVALID_VALUE.left()
      PropertyType.BOOLEAN -> rawValue.equals("true", ignoreCase = true).toString().right()
      PropertyType.STRING -> rawValue.right()
      PropertyType.DATE -> if (from == PropertyType.DATETIME) truncateDatetimeToDate(rawValue).right() else rawValue.right()
      PropertyType.DATETIME -> rawValue.right()
      else -> ValueConversionError.UNSUPPORTED_CONVERSION.left()
    }
  }

  /** Converts [rawValue] (numeric text expressed in [sourceGranularity]) to [unit]'s `storageGranularity`. Returns [rawValue] unchanged if it is not numeric or already in that granularity. */
  fun convertGranularity(unit: PropertyUnit, sourceGranularity: Granularity, rawValue: String): String {
    if (sourceGranularity == unit.storageGranularity) return rawValue
    val amount = rawValue.toBigDecimalOrNull() ?: return rawValue
    val converted = amount.multiply(BigDecimal(sourceGranularity.factorToSmallestUnit))
      .divide(BigDecimal(unit.storageGranularity.factorToSmallestUnit), MathContext.DECIMAL64)
    return formatDouble(converted)
  }

  private fun formatDouble(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

  private fun truncateDatetimeToDate(rawValue: String): String = rawValue.substringBefore('T').substringBefore(' ')
}
