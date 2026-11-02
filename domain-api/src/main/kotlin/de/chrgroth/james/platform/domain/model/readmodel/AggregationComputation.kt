package de.chrgroth.james.platform.domain.model.readmodel

import de.chrgroth.james.platform.domain.model.app.AggregationDefinition
import de.chrgroth.james.platform.domain.model.app.AggregationFunction
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.decodeObjectValue
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.TimeBucket
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

/**
 * Shared, pure computation rules for [AggregationDefinition], used both by the inline O(1) delta updates
 * (`AggregationInlineUpdateService`) and the full-scan outbox recompute (`AggregationRecomputeService`) - see
 * docs/adr/0020-aggregation-definitions.md.
 */

/**
 * The value [item] contributes to this aggregation's running computation, or null if it does not contribute
 * (missing/blank source property, or - for SUM/AVG/MIN/MAX - a non-numeric value). For COUNT, every non-null
 * occurrence contributes exactly `1.0`, matching [AggregationFunction.requiresNumericSourceProperty].
 */
fun AggregationDefinition.contributionOf(item: AppData): Double? {
  val raw = sourceValueOf(item)
  return if (function == AggregationFunction.COUNT) {
    if (raw.isNullOrBlank()) null else 1.0
  } else {
    raw?.toDoubleOrNull()
  }
}

/**
 * The raw value of [AggregationDefinition.sourceProperty] in [item]: read directly for a top-level property, else from the JSON value of the
 * enclosing OBJECT properties along [AggregationDefinition.sourcePath] (nested objects are stored as maps keyed by property id, nested
 * scalar values as strings). Null if any level is missing or not an object.
 */
fun AggregationDefinition.sourceValueOf(item: AppData): String? {
  if (sourcePath.isEmpty()) return item.data[sourceProperty.value]
  var current: Map<*, *> = runCatching { decodeObjectValue(item.data[sourcePath.first().value]) }.getOrNull() ?: return null
  for (parent in sourcePath.drop(1)) {
    current = current[parent.value] as? Map<*, *> ?: return null
  }
  return current[sourceProperty.value]?.toString()
}

/**
 * The group this aggregation's value for [item] belongs to: the AppData id of the referenced Entity instance
 * (via [AggregationDefinition.refPath]) if set, else the stringified value of [AggregationDefinition.groupBy] if
 * set, else null for a single, ungrouped value across the owning Entity. [AggregationDefinition.refPath] and
 * [AggregationDefinition.groupBy] are mutually exclusive (enforced by the Version validation), so at most one applies.
 */
fun AggregationDefinition.groupKeyOf(item: AppData): String? =
  refPath?.let { item.data[it.value] } ?: groupBy?.let { item.data[it.value] }

/**
 * The time bucket [item] falls into, keyed by [AggregationDefinition.timeProperty] if set (a DATE/DATETIME
 * property of [item]'s Entity), else by [AppData.createdAt]; null when [AggregationDefinition.timeBucket] is
 * unset, or when [timeProperty] is set but [item] has no (parseable) value for it.
 */
fun AggregationDefinition.bucketKeyOf(item: AppData): String? = timeBucket?.let { bucket -> bucketDateOf(item)?.let { encodeTimeBucket(bucket, it, periodStart) } }

private fun AggregationDefinition.bucketDateOf(item: AppData): LocalDate? {
  val timeProperty = timeProperty ?: return item.createdAt.atZone(ZoneOffset.UTC).toLocalDate()
  val raw = item.data[timeProperty.value] ?: return null
  return runCatching { LocalDate.parse(raw) }.getOrNull() ?: runCatching { LocalDateTime.parse(raw).toLocalDate() }.getOrNull()
}

/** Calendar default period start: a "year"/"quarter" begins 01.01, exactly the pre-existing JAHR behavior. */
private val CALENDAR_PERIOD_START: MonthDay = MonthDay.of(1, 1)

/**
 * The key of the [bucket] [date] falls into: `2026-09-25` (TAG), `2026-W39` (ISO week), `2026-09` (MONAT), `2026-Q1` (QUARTAL) or `2026` (JAHR);
 * JAHR and QUARTAL count from [periodStart] (calendar default 01.01.). Keys of one bucket sort chronologically as text.
 */
fun encodeTimeBucket(bucket: TimeBucket, date: LocalDate, periodStart: MonthDay?): String {
  return when (bucket) {
    TimeBucket.TAG -> date.toString()
    TimeBucket.WOCHE -> {
      val weekFields = WeekFields.ISO
      "%04d-W%02d".format(date.get(weekFields.weekBasedYear()), date.get(weekFields.weekOfWeekBasedYear()))
    }
    TimeBucket.MONAT -> "%04d-%02d".format(date.year, date.monthValue)
    TimeBucket.JAHR -> periodYearOf(date, periodStart ?: CALENDAR_PERIOD_START).toString()
    TimeBucket.QUARTAL -> {
      val start = periodStart ?: CALENDAR_PERIOD_START
      val year = periodYearOf(date, start)
      val periodStartDate = LocalDate.of(year, start.monthValue, start.dayOfMonth)
      val monthsSincePeriodStart = ChronoUnit.MONTHS.between(periodStartDate, date)
      "%04d-Q%d".format(year, monthsSincePeriodStart / 3 + 1)
    }
  }
}

/**
 * The "period year" [date] belongs to for a [periodStart]-anchored JAHR/QUARTAL: the year of [date] if [date] is on
 * or after that year's [periodStart], else the previous year — e.g. with [periodStart] 10.04., 09.04.2026 belongs to
 * period year 2025, while 10.04.2026 already belongs to 2026.
 */
private fun periodYearOf(date: LocalDate, periodStart: MonthDay): Int = if (MonthDay.from(date) >= periodStart) date.year else date.year - 1

private val WEEK_BUCKET_REGEX = Regex("""(\d{4})-W(\d{2})""")
private val MONTH_BUCKET_REGEX = Regex("""(\d{4})-(\d{2})""")
private val QUARTER_BUCKET_REGEX = Regex("""(\d{4})-Q(\d)""")
private val BUCKET_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private const val QUARTERS_PER_YEAR = 4

/** The key of the bucket following [key] (see [encodeTimeBucket]), e.g. `2026-12` -> `2027-01`; null if [key] is no valid key of [bucket]. */
fun nextTimeBucket(bucket: TimeBucket, key: String): String? = when (bucket) {
  TimeBucket.TAG -> runCatching { LocalDate.parse(key).plusDays(1).toString() }.getOrNull()
  TimeBucket.WOCHE -> WEEK_BUCKET_REGEX.matchEntire(key)?.let { match ->
    val monday = LocalDate.of(match.groupValues[1].toInt(), 1, 4)
      .with(WeekFields.ISO.weekOfWeekBasedYear(), match.groupValues[2].toLong())
      .with(DayOfWeek.MONDAY)
    encodeTimeBucket(TimeBucket.WOCHE, monday.plusWeeks(1), null)
  }
  TimeBucket.MONAT -> runCatching { YearMonth.parse(key).plusMonths(1).toString() }.getOrNull()
  TimeBucket.QUARTAL -> QUARTER_BUCKET_REGEX.matchEntire(key)?.let { match ->
    val year = match.groupValues[1].toInt()
    val quarter = match.groupValues[2].toInt()
    if (quarter < QUARTERS_PER_YEAR) "%04d-Q%d".format(year, quarter + 1) else "%04d-Q1".format(year + 1)
  }
  TimeBucket.JAHR -> key.toIntOrNull()?.let { (it + 1).toString() }
}

/** Formats a bucket key (see [encodeTimeBucket]) for display, e.g. "25.09.2026", "KW 39/2026", "09/2026", "Q1/2026", "2026". */
fun formatTimeBucketLabel(bucket: TimeBucket, key: String): String = when (bucket) {
  TimeBucket.TAG -> runCatching { LocalDate.parse(key) }.getOrNull()?.let { BUCKET_DATE_FORMATTER.format(it) } ?: key
  TimeBucket.WOCHE -> WEEK_BUCKET_REGEX.matchEntire(key)?.let { "KW ${it.groupValues[2].toInt()}/${it.groupValues[1]}" } ?: key
  TimeBucket.MONAT -> MONTH_BUCKET_REGEX.matchEntire(key)?.let { "${it.groupValues[2]}/${it.groupValues[1]}" } ?: key
  TimeBucket.QUARTAL -> QUARTER_BUCKET_REGEX.matchEntire(key)?.let { "Q${it.groupValues[2]}/${it.groupValues[1]}" } ?: key
  TimeBucket.JAHR -> key
}

/** Builds the [AggregationValueId] this aggregation's value for [item] is stored under, for installation [installedAppId]. */
fun AggregationDefinition.valueIdFor(installedAppId: InstalledAppId, item: AppData): AggregationValueId =
  AggregationValueId(installedAppId, id, groupKeyOf(item), bucketKeyOf(item))

/**
 * Full, authoritative recomputation of every group/bucket of this aggregation across [items] (all AppData of the
 * owning Entity), scanning once - see docs/adr/0020-aggregation-definitions.md, the O(n) outbox recompute path.
 */
fun AggregationDefinition.computeAggregationValues(installedAppId: InstalledAppId, items: List<AppData>, now: Instant): List<AggregationValue> =
  items.mapNotNull { item -> contributionOf(item)?.let { contribution -> Triple(groupKeyOf(item), bucketKeyOf(item), contribution) } }
    .groupBy { it.first to it.second }
    .map { (key, entries) ->
      val (groupKey, bucketKey) = key
      val contributions = entries.map { it.third }
      val value = when (function) {
        AggregationFunction.SUM, AggregationFunction.COUNT -> contributions.sum()
        AggregationFunction.AVG -> contributions.average()
        AggregationFunction.MIN -> contributions.min()
        AggregationFunction.MAX -> contributions.max()
      }
      AggregationValue(
        id = AggregationValueId(installedAppId, id, groupKey, bucketKey),
        value = value,
        status = AggregationValueStatus.UP_TO_DATE,
        updatedAt = now,
        sampleCount = contributions.size.toLong(),
      )
    }
