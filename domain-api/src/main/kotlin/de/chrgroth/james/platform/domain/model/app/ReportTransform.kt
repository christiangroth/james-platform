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

// region advanced transforms

/**
 * `{ delta: stand, as: verbrauch, perDay: true, by: zaehler }`: the difference of [field] to the previous row with a value (chronologically, per
 * group of a grouped aggregate or per value of [by]), e.g. the consumption between two meter readings; the first row has none. With [perDay] the
 * difference is divided by the days between both rows. With [bucket] the rows are replaced by one row per bucket (and [by] value): each difference
 * is spread evenly over the days between both readings and summed per bucket (field `bucket`, the [by] field and [output]). [output] defaults to
 * `<field>_delta`.
 */
data class ReportDeltaTransform(
  val field: String,
  val output: String = field + SUFFIX,
  val perDay: Boolean = false,
  val by: String? = null,
  val bucket: TimeBucket? = null,
  val periodStart: MonthDay? = null,
) : ReportTransform {
  override val key: String get() = KEY

  companion object {
    const val KEY = "delta"
    const val SUFFIX = "_delta"
  }
}

/**
 * How two queries' rows are matched: [field] of this query against [other] of the other query; [other] [OBJECT_ID] matches the other row's
 * object id, e.g. a reference field against the referenced objects.
 */
data class ReportJoinKey(val field: String, val other: String = field) {
  companion object {
    const val OBJECT_ID = "id"
  }
}

/**
 * `{ asOf: vertraege, fields: [wochenstunden, urlaubstage], on: arbeitgeber, at: start, prefix: vertrag_ }`: "value valid at date" - attaches
 * [fields] of the latest row of the other [query] whose date (time field, or bucket start) is at most this row's date (time field, or the
 * bucket's start - its end with [atEnd]). [on] restricts the candidates to rows with the same key. Field names get [prefix].
 */
data class ReportAsOfTransform(
  val query: String,
  val fields: List<String>,
  val on: ReportJoinKey? = null,
  val atEnd: Boolean = false,
  val prefix: String = "",
) : ReportTransform {
  override val key: String get() = KEY

  companion object {
    const val KEY = "asOf"
  }
}

/**
 * `{ join: vertraege, on: { field: vertrag, other: id }, fields: [arbeitgeber, jahresbrutto], prefix: vertrag_ }`: attaches [fields] of the first
 * row of the other [query] whose key matches (left join: rows without match keep empty values). Field names get [prefix].
 */
data class ReportJoinTransform(
  val query: String,
  val on: ReportJoinKey,
  val fields: List<String>,
  val prefix: String = "",
) : ReportTransform {
  override val key: String get() = KEY

  companion object {
    const val KEY = "join"
  }
}

/**
 * `{ top: dauer, n: 3, order: asc, by: strecke, bucket: JAHR, as: rang }`: records / top-N - keeps the [n] rows with the highest (lowest with
 * [ascending]) value of [field], per value of [by] and/or per time [bucket] (of the row's date; adds the field `bucket`), and numbers them in
 * [output] (default `rang`). The rows are ordered by bucket, [by] value and rank; rows without value are dropped.
 */
data class ReportTopTransform(
  val field: String,
  val n: Int = 1,
  val ascending: Boolean = false,
  val by: String? = null,
  val bucket: TimeBucket? = null,
  val periodStart: MonthDay? = null,
  val output: String = RANK,
) : ReportTransform {
  override val key: String get() = KEY

  companion object {
    const val KEY = "top"
    const val RANK = "rang"
  }
}

/**
 * `{ goal: km, target: config.zielKm, period: JAHR, as: km_ziel, progress: km_fortschritt }`: target vs. actual - [output] (default
 * `<field>_ziel`) is the [target] (a number, a unit text such as `1000km`, `config.<field>` or a numeric field of the row, e.g. attached by
 * `asOf`), [progress] (default `<field>_fortschritt`) the value of [field] in percent of it. With [period] the target applies per such period and
 * each row gets its pro-rata share up to the row's date (end of its bucket), e.g. the cumulative yearly goal per month.
 */
data class ReportGoalTransform(
  val field: String,
  val target: String,
  val period: TimeBucket? = null,
  val periodStart: MonthDay? = null,
  val output: String = field + SUFFIX,
  val progress: String = field + PROGRESS_SUFFIX,
) : ReportTransform {
  override val key: String get() = KEY

  companion object {
    const val KEY = "goal"
    const val SUFFIX = "_ziel"
    const val PROGRESS_SUFFIX = "_fortschritt"
  }
}

// endregion

// region kotlin step

/**
 * `{ kotlin: "<script>", fields: { ersparnis: CENTS, breakEven: DATE } }`: the escape hatch for calculations the declarative steps cannot
 * express (e.g. a PV break-even). The [script] runs backend-side via the JSR-223 Kotlin engine with the same timeout as computed properties (ADR
 * 0008, ADR 0028) and sees the bindings `rows` (the result rows so far, `List<Map<String, String?>>` of raw storage values by field), `config`
 * (the App configuration's values by field name) and `today` (`java.time.LocalDate`). Its last expression is the new list of rows (an iterable of
 * maps from field name to value; numbers, dates, booleans and texts are stored as raw values). [fields] declares the fields the script adds or
 * retypes, so panels can be validated against them; the other result fields stay as they are. A failing or timed-out script turns the panels
 * reading the query into an error message instead of failing the report.
 */
data class ReportKotlinTransform(val script: String, val fields: List<ReportScriptField> = emptyList()) : ReportTransform {
  override val key: String get() = KEY

  companion object {
    const val KEY = "kotlin"
  }
}

/** A field a `kotlin` step adds: [type] LONG, DOUBLE, STRING, DATE, DATETIME or BOOLEAN; [unit] (numeric only) the granularity values are in. */
data class ReportScriptField(val name: String, val type: PropertyType, val unit: PropertyUnit? = null)

// endregion
