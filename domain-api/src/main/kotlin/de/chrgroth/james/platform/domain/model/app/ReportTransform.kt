package de.chrgroth.james.platform.domain.model.app

import java.time.MonthDay

// region variables

/**
 * A report variable, shown as a control on top of the report and referenced as `$name`: as a query `range` or as a filter `value`. The
 * selection travels with the request (`var.<name>`); an unknown or missing selection falls back to the default.
 */
sealed interface ReportVariable {
  val name: String

  /** The control's label; the name without one. */
  val label: String?
}

/**
 * `{ type: timeRange, default: last_12_months, options: [...] }`: one of the relative periods (see [RelativePeriod]) or [ALL] (no restriction).
 * [options] lists the selectable keys in order (all relative periods plus [ALL] without an explicit list), [default] the preselected key
 * (the first option without one).
 */
data class ReportTimeRangeVariable(
  override val name: String,
  override val label: String? = null,
  val options: List<String> = DEFAULT_OPTIONS,
  val default: String = options.first(),
) : ReportVariable {
  companion object {
    const val ALL = "all"
    val DEFAULT_OPTIONS: List<String> = RelativePeriod.entries.map { it.key } + ALL
  }
}

/**
 * `{ type: select, query: jahre, field: bucket }`: the distinct non-empty values of [field] in the result of [query] (in result order) are
 * the options, labelled like table cells (references by display text, buckets as `09/2026`). With [includeAll] an extra first option "Alle"
 * (empty value) drops the filter rules using the variable. [default]: [FIRST] (default), [LAST], [ALL_VALUE] (needs [includeAll]) or a value.
 * Used as a range, the field must be the time bucket of an aggregate query: the selected bucket's period becomes the range.
 */
data class ReportSelectVariable(
  override val name: String,
  override val label: String? = null,
  val query: String,
  val field: String,
  val includeAll: Boolean = false,
  val default: String = FIRST,
) : ReportVariable {
  companion object {
    const val FIRST = "first"
    const val LAST = "last"
    const val ALL_VALUE = "all"
  }
}

// endregion

// region transforms

/**
 * One step of a query's transformation pipeline (`transforms: { <query>: [steps] }`), applied in order to the query's result rows (after
 * filter, aggregation, sort and limit). Steps add or replace result fields; [ReportResampleTransform] replaces the rows. Running steps
 * (cumulative, moving average) process the rows chronologically (by bucket, time field, else creation time) and per group of a grouped
 * aggregate, whatever order the rows are shown in.
 */
sealed interface ReportTransform {
  /** The YAML key of the step, e.g. `cumulative`. */
  val key: String
}

/**
 * `{ calc: bmi, expr: "gewicht / (groesse / 100) ^ 2", unit: KILOGRAMS }`: a new DOUBLE field [name] computed per row by a small, safe
 * arithmetic [expression] over the row's numeric fields (raw storage values, e.g. meters, cents) and `config.<field>` values (raw, see ADR
 * 0027): numbers, `+ - * / % ^`, parentheses and the functions abs, sqrt, round, floor, ceil, min, max, coalesce. A missing operand or a
 * division by zero yields no value. [unit] (a granularity name such as `SECONDS`) makes the result a unit value in that granularity.
 */
data class ReportCalcTransform(val name: String, val expression: String, val unit: PropertyUnit? = null) : ReportTransform {
  override val key: String get() = KEY

  companion object {
    const val KEY = "calc"
  }
}

/** `{ cumulative: distanz, as: gesamt }`: running total of [field]; [output] defaults to [field] (replaced in place). Empty values add 0. */
data class ReportCumulativeTransform(val field: String, val output: String = field) : ReportTransform {
  override val key: String get() = KEY

  companion object {
    const val KEY = "cumulative"
  }
}

/** `{ movingAverage: gewicht, window: 7, as: gewicht_7 }`: average of [field] over the last [window] rows with a value (incl. the current one). */
data class ReportMovingAverageTransform(val field: String, val window: Int, val output: String = field) : ReportTransform {
  override val key: String get() = KEY

  companion object {
    const val KEY = "movingAverage"
  }
}

/**
 * `{ yoy: km, as: km_vorjahr }`: the value of [field] in the bucket one year earlier (same month, quarter, ...), read from the same query over
 * its range shifted back one year. Needs a time bucket. [output] defaults to `<field>_vorjahr`.
 */
data class ReportYoyTransform(val field: String, val output: String = field + SUFFIX) : ReportTransform {
  override val key: String get() = KEY

  companion object {
    const val KEY = "yoy"
    const val SUFFIX = "_vorjahr"
  }
}

/**
 * `{ forecast: km, as: km_prognose }`: [field] extrapolated linearly to the end of its period - per time bucket (the bucket containing today
 * is scaled by elapsed days, completed buckets keep their value, future ones have none) or, without bucket, over the query's closed range.
 * [output] defaults to `<field>_prognose`.
 */
data class ReportForecastTransform(val field: String, val output: String = field + SUFFIX) : ReportTransform {
  override val key: String get() = KEY

  companion object {
    const val KEY = "forecast"
    const val SUFFIX = "_prognose"
  }
}

/**
 * `{ resample: MONAT, fill: forward }`: one row per [bucket] from the first to the last bucket with data, carrying the values of the latest
 * row in each bucket (by time field, or by bucket of an aggregate query). With [fillForward] a bucket without rows repeats the previous
 * bucket's values ("value valid at"), else it stays empty. The result gets a `bucket` field and charts plot it over buckets.
 */
data class ReportResampleTransform(val bucket: TimeBucket, val periodStart: MonthDay? = null, val fillForward: Boolean = true) : ReportTransform {
  override val key: String get() = KEY

  companion object {
    const val KEY = "resample"
  }
}

// endregion
