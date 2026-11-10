package de.chrgroth.james.platform.domain.model.app

/**
 * Family of units a [PropertyUnit] belongs to. Determines which [Granularity] enum ([TimeGranularity], [DistanceGranularity],
 * [CurrencyGranularity], [MassGranularity], [EnergyGranularity] or [VolumeGranularity]) is valid for it.
 *
 * [compositeFormat] decides how [formatUnitValue] renders a value: as a composite over all granularities (`"15km 400m"`), or as a single
 * amount in the storage granularity (`"75,3kg"`) for families whose granularities do not read well combined (`"2kWh 300kcal"`).
 */
enum class UnitFamily(val compositeFormat: Boolean) {
  TIME(compositeFormat = true),
  DISTANCE(compositeFormat = true),

  /** Money, always in euros - there is deliberately no currency code and no exchange-rate conversion. */
  CURRENCY(compositeFormat = false),

  /** Weight, e.g. body weight in kilograms. */
  MASS(compositeFormat = false),

  /** Energy, e.g. food energy in kilocalories or electricity in kilowatt hours; both convert exactly via millijoules. */
  ENERGY(compositeFormat = false),

  /** Volume, e.g. drinking water in milliliters or gas/water meter readings in cubic meters. */
  VOLUME(compositeFormat = false),
}

/** A single step of granularity within a [UnitFamily], e.g. `METERS` for [DistanceGranularity]. */
sealed interface Granularity {
  val family: UnitFamily

  /** Short symbol used in [UnitFormat] text parsing/formatting, e.g. `"km"`. */
  val symbol: String

  /** Conversion factor to the smallest granularity of [family], e.g. `1_000` for `METERS` when the smallest unit is `MILLIMETERS`. */
  val factorToSmallestUnit: Long
}

enum class TimeGranularity(override val symbol: String, override val factorToSmallestUnit: Long) : Granularity {
  MILLISECONDS("ms", 1L),
  SECONDS("s", 1_000L),
  MINUTES("min", 60_000L),
  HOURS("h", 3_600_000L),
  DAYS("d", 86_400_000L),
  ;

  override val family: UnitFamily = UnitFamily.TIME
}

enum class DistanceGranularity(override val symbol: String, override val factorToSmallestUnit: Long) : Granularity {
  MILLIMETERS("mm", 1L),
  CENTIMETERS("cm", 10L),
  METERS("m", 1_000L),
  KILOMETERS("km", 1_000_000L),
  ;

  override val family: UnitFamily = UnitFamily.DISTANCE
}

/** Granularities of [UnitFamily.CURRENCY]: euro amounts, stored e.g. in [CENTS] to keep a `LONG` field exact. */
enum class CurrencyGranularity(override val symbol: String, override val factorToSmallestUnit: Long) : Granularity {
  CENTS("ct", 1L),
  EUROS("€", 100L),
  ;

  override val family: UnitFamily = UnitFamily.CURRENCY
}

/** Granularities of [UnitFamily.MASS], with milligrams as the smallest unit. */
enum class MassGranularity(override val symbol: String, override val factorToSmallestUnit: Long) : Granularity {
  MILLIGRAMS("mg", 1L),
  GRAMS("g", 1_000L),
  KILOGRAMS("kg", 1_000_000L),
  ;

  override val family: UnitFamily = UnitFamily.MASS
}

/**
 * Granularities of [UnitFamily.ENERGY]. The factors are expressed in millijoules (not itself a granularity), which keeps every
 * conversion exact: 1 kcal = 4.184 kJ, 1 Wh = 3.6 kJ.
 */
enum class EnergyGranularity(override val symbol: String, override val factorToSmallestUnit: Long) : Granularity {
  KILOJOULES("kJ", 1_000_000L),
  KILOCALORIES("kcal", 4_184_000L),
  WATT_HOURS("Wh", 3_600_000L),
  KILOWATT_HOURS("kWh", 3_600_000_000L),
  ;

  override val family: UnitFamily = UnitFamily.ENERGY
}

/** Granularities of [UnitFamily.VOLUME], with milliliters as the smallest unit. */
enum class VolumeGranularity(override val symbol: String, override val factorToSmallestUnit: Long) : Granularity {
  MILLILITERS("ml", 1L),
  LITERS("l", 1_000L),
  CUBIC_METERS("m³", 1_000_000L),
  ;

  override val family: UnitFamily = UnitFamily.VOLUME
}

/**
 * A unit assigned to a `LONG`/`DOUBLE` [Property]. Values are always stored numerically in [storageGranularity],
 * the field's fixed smallest granularity — chosen once at field creation and immutable afterward, so changing it
 * requires recreating the field (see ADR 0016); a `CopyValue` migration step can carry existing values over into
 * the new field, converting granularity via [ValueConversion.convertGranularity] (see ADR 0023). [defaultGranularity]
 * is only a UI default for new input and may change freely at any time. [maxDisplayGranularity] optionally caps the largest
 * granularity a composite value is shown in (e.g. `HOURS`: "55h 15min" instead of "2d 7h 15min"); without it all of the family's
 * granularities are used. It only affects display and may change freely, too.
 */
data class PropertyUnit(
  val family: UnitFamily,
  val storageGranularity: Granularity,
  val defaultGranularity: Granularity,
  val maxDisplayGranularity: Granularity? = null,
) {
  init {
    require(storageGranularity.family == family) { "storageGranularity $storageGranularity does not belong to family $family" }
    require(defaultGranularity.family == family) { "defaultGranularity $defaultGranularity does not belong to family $family" }
    require(maxDisplayGranularity == null || isValidMaxDisplayGranularity(storageGranularity, maxDisplayGranularity)) {
      "maxDisplayGranularity $maxDisplayGranularity must belong to family $family and not be finer than storageGranularity $storageGranularity"
    }
  }

  companion object {
    /** Whether [maxDisplay] can cap the display of values stored in [storage]: the same family and at least as coarse as [storage]. */
    fun isValidMaxDisplayGranularity(storage: Granularity, maxDisplay: Granularity): Boolean =
      maxDisplay.family == storage.family && maxDisplay.factorToSmallestUnit >= storage.factorToSmallestUnit
  }
}
