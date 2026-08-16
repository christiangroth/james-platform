package de.chrgroth.james.platform.domain.model.app

import java.math.BigDecimal
import java.math.MathContext

/** Human-readable hint describing the textual format accepted for a [UnitFamily]'s values, shown as input placeholder / help text. */
fun unitFormatHint(family: UnitFamily): String = when (family) {
  UnitFamily.TIME -> "1d 2h 30min 15s"
  UnitFamily.DISTANCE -> "15km 400m"
}

private const val NUMBER_PATTERN = """-?\d+(?:[.,]\d+)?"""
private val NUMBER_FORMAT = Regex("^$NUMBER_PATTERN$")

/**
 * Parses a `LONG`/`DOUBLE` property's textual value into a [BigDecimal] expressed in [storageGranularity].
 * Accepts two formats, with [raw] trimmed before matching:
 * - A bare number (`.`/`,` as decimal separator), read as already expressed in [storageGranularity].
 * - Unit-suffixed, e.g. "15km 400m", composed of any subset/repetition of [storageGranularity]'s family's
 *   granularity symbols, each with its own (possibly decimal) amount.
 * Returns null if [raw] does not match either format, is empty, or mixes symbols from another family.
 */
fun parseUnitValue(raw: String, storageGranularity: Granularity): BigDecimal? {
  val trimmed = raw.trim()
  if (trimmed.isEmpty()) return null

  parseNumber(trimmed)?.let { return it }

  val granularities = granularitiesOf(storageGranularity.family)
  val symbolPattern = granularities.sortedByDescending { it.symbol.length }.joinToString("|") { Regex.escape(it.symbol) }
  val format = Regex("""^(?:$NUMBER_PATTERN\s*(?:$symbolPattern)\s*)+$""")
  if (!format.matches(trimmed)) return null

  val token = Regex("""($NUMBER_PATTERN)\s*($symbolPattern)""")
  var totalInSmallestUnit = BigDecimal.ZERO
  for (match in token.findAll(trimmed)) {
    val (numberPart, symbolPart) = match.destructured
    val amount = parseNumber(numberPart) ?: return null
    val granularity = granularities.first { it.symbol == symbolPart }
    totalInSmallestUnit += amount * BigDecimal(granularity.factorToSmallestUnit)
  }
  return totalInSmallestUnit.divide(BigDecimal(storageGranularity.factorToSmallestUnit), MathContext.DECIMAL64)
}

private fun parseNumber(raw: String): BigDecimal? {
  if (!NUMBER_FORMAT.matches(raw)) return null
  return raw.replace(',', '.').toBigDecimalOrNull()
}

private fun granularitiesOf(family: UnitFamily): List<Granularity> = when (family) {
  UnitFamily.TIME -> TimeGranularity.entries
  UnitFamily.DISTANCE -> DistanceGranularity.entries
}

/** Formats [value] (expressed in [storageGranularity]) as a unit-suffixed composite string, e.g. "15km 400m", accepted back by [parseUnitValue]. */
fun formatUnitValue(value: BigDecimal, storageGranularity: Granularity): String {
  val granularities = granularitiesOf(storageGranularity.family).sortedByDescending { it.factorToSmallestUnit }
  var remaining = value.multiply(BigDecimal(storageGranularity.factorToSmallestUnit))
  val parts = mutableListOf<String>()
  granularities.forEachIndexed { index, granularity ->
    val factor = BigDecimal(granularity.factorToSmallestUnit)
    val isSmallest = index == granularities.lastIndex
    val amount = if (isSmallest) remaining.divide(factor, MathContext.DECIMAL64) else remaining.divideToIntegralValue(factor)
    if (amount.signum() != 0) {
      parts += "${amount.stripTrailingZeros().toPlainString()}${granularity.symbol}"
    }
    if (!isSmallest) remaining -= amount.multiply(factor)
  }
  return if (parts.isEmpty()) "0${granularities.last().symbol}" else parts.joinToString(" ")
}
