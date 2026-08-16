package de.chrgroth.james.platform.domain.model.app

/** Family of units a [PropertyUnit] belongs to. Determines which [Granularity] enum ([TimeGranularity] or [DistanceGranularity]) is valid for it. */
enum class UnitFamily {
  TIME,
  DISTANCE,
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

/**
 * A unit assigned to a `LONG`/`DOUBLE` [Property]. Values are always stored numerically in [storageGranularity],
 * the field's fixed smallest granularity — chosen once at field creation and immutable afterward, since there is
 * no migration mechanism for existing data; changing it requires recreating the field (see ADR 0016). [defaultGranularity]
 * is only a UI default for new input and may change freely at any time.
 */
data class PropertyUnit(
  val family: UnitFamily,
  val storageGranularity: Granularity,
  val defaultGranularity: Granularity,
) {
  init {
    require(storageGranularity.family == family) { "storageGranularity $storageGranularity does not belong to family $family" }
    require(defaultGranularity.family == family) { "defaultGranularity $defaultGranularity does not belong to family $family" }
  }
}
