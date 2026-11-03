package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.ReportAggregate
import de.chrgroth.james.platform.domain.model.app.ReportCalendar
import de.chrgroth.james.platform.domain.model.app.ReportCheck
import de.chrgroth.james.platform.domain.model.app.ReportCheckPeriod
import de.chrgroth.james.platform.domain.model.app.ReportDefinition
import de.chrgroth.james.platform.domain.model.app.ReportGapCheck
import de.chrgroth.james.platform.domain.model.app.ReportRuleCheck
import de.chrgroth.james.platform.domain.model.app.WarningSpec
import de.chrgroth.james.platform.domain.model.readmodel.encodeTimeBucket
import de.chrgroth.james.platform.domain.model.readmodel.formatTimeBucketLabel
import de.chrgroth.james.platform.domain.model.readmodel.nextTimeBucket
import de.chrgroth.james.platform.domain.report.ReportExecutor.QueryResult
import de.chrgroth.james.platform.domain.report.ReportExecutor.ResultRow
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Executes the [ReportCheck]s of a report (see arc42 "Reports", checks) through [executor], which runs their queries with the current
 * variable selections, and turns their findings into [WarningSpec]s (each check at most once per report execution).
 */
internal class ReportChecker(private val executor: ReportExecutor, private val definition: ReportDefinition, private val today: LocalDate) {

  private val findings = HashMap<String, List<WarningSpec>>()

  /** The findings of [check]: gaps newest first, rule violations in the query's row order. */
  fun warnings(check: ReportCheck): List<WarningSpec> = findings.getOrPut(check.name) {
    when (check) {
      is ReportGapCheck -> gaps(check)
      is ReportRuleCheck -> violations(check)
    }
  }

  // region gaps

  private fun gaps(check: ReportGapCheck): List<WarningSpec> {
    val query = definition.query(check.query) ?: return emptyList()
    val timeField = query.timeField ?: return emptyList()
    val result = executor.run(query) ?: return emptyList()
    val dates = result.rows.mapNotNull { ReportFields.parseDate(it.values[timeField]) }
    val range = executor.rangeOf(query)
    val from = range?.from ?: dates.minOrNull() ?: return emptyList()
    val to = listOfNotNull(range?.to, today).min()
    if (to.isBefore(from)) return emptyList()

    val bucket = check.every.bucket
    val present = dates.mapTo(HashSet()) { encodeTimeBucket(bucket, it, null) }
    val currentKey = encodeTimeBucket(bucket, today, null)
    val excluded = check.exclude.flatMapTo(HashSet()) { calendarDates(it) }
    val last = encodeTimeBucket(bucket, to, null)
    val warnings = mutableListOf<WarningSpec>()
    var key: String? = encodeTimeBucket(bucket, from, null)
    var periods = 0
    while (key != null && key <= last && periods < MAX_PERIODS) {
      periods++
      val skipCurrent = key == currentKey && !check.current
      if (key !in present && !skipCurrent && expected(check.every, key, from, to, excluded)) {
        val label = formatTimeBucketLabel(bucket, key)
        warnings += warning(check, label, fill(check.message ?: GAP_MESSAGE, mapOf(ReportCheck.PERIOD_PLACEHOLDER to label)))
      }
      key = nextTimeBucket(bucket, key)
    }
    return warnings.asReversed()
  }

  /** Whether an entry is expected in the period [key]: some of its days within [from]..[to] (weekdays only for WERKTAG) are not [excluded]. */
  private fun expected(period: ReportCheckPeriod, key: String, from: LocalDate, to: LocalDate, excluded: Set<LocalDate>): Boolean {
    val range = ReportBuckets.period(period.bucket, key, null) ?: return false
    val start = maxOf(range.from!!, from)
    val end = minOf(range.to!!, to)
    return days(start, end).any { day -> (period != ReportCheckPeriod.WERKTAG || day.dayOfWeek !in WEEKEND) && day !in excluded }
  }

  private fun calendarDates(calendar: ReportCalendar): List<LocalDate> = when (calendar) {
    is ReportCalendar.Config -> executor.configDates(calendar.field)
    is ReportCalendar.Query -> {
      val query = definition.query(calendar.query)
      val field = calendar.field ?: query?.timeField
      val result = if (query != null && field != null) executor.run(query) else null
      result?.rows.orEmpty().flatMap { row ->
        val start = ReportFields.parseDate(row.values[field]) ?: return@flatMap emptyList()
        val end = calendar.to?.let { ReportFields.parseDate(row.values[it]) }?.takeIf { !it.isBefore(start) } ?: start
        days(start, minOf(end, start.plusDays(MAX_SPAN_DAYS))).toList()
      }
    }
  }

  private fun days(from: LocalDate, to: LocalDate): Sequence<LocalDate> = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }

  // endregion

  // region rules

  private fun violations(check: ReportRuleCheck): List<WarningSpec> {
    val query = definition.query(check.query) ?: return emptyList()
    val result = executor.result(query) ?: return emptyList()
    val unit = check.field?.let { result.columns[it] }?.unit
    val expression = check.expression?.let { ReportExpressions.parseOrNull(it) }
    val min = check.min?.let { bound(it, unit) }
    val max = check.max?.let { bound(it, unit) }
    val valueRule = check.field != null || expression != null
    return result.rows.flatMap { row ->
      val warnings = mutableListOf<WarningSpec>()
      if (check.required.isNotEmpty() && check.whenPresent.all { present(row, it) }) {
        val missing = check.required.filterNot { present(row, it) }
        if (missing.isNotEmpty()) {
          val values = placeholders(result, row) + (ReportCheck.MISSING_PLACEHOLDER to missing.joinToString(", "))
          warnings += warning(check, label(query.timeField, result, row), fill(check.message ?: REQUIRED_MESSAGE, values))
        }
      }
      if (valueRule) {
        val value = if (check.field != null) row.values[check.field]?.toDoubleOrNull() else expression?.evaluateNumber(context(result, row))
        if (value != null && ((min != null && value < min) || (max != null && value > max))) {
          val values = placeholders(result, row) + mapOf(
            ReportCheck.VALUE_PLACEHOLDER to executor.formatNumber(value, unit),
            ReportCheck.MIN_PLACEHOLDER to (min?.let { executor.formatNumber(it, unit) } ?: NO_VALUE),
            ReportCheck.MAX_PLACEHOLDER to (max?.let { executor.formatNumber(it, unit) } ?: NO_VALUE),
          )
          warnings += warning(check, label(query.timeField, result, row), fill(check.message ?: rangeMessage(min, max), values))
        }
      }
      warnings
    }
  }

  private fun context(result: QueryResult, row: ResultRow) = ReportRowContext(row.values, result.columns, executor::configValue, today, executor::text)

  private fun bound(raw: String, unit: PropertyUnit?): Double? =
    if (ReportFields.configPath(raw) != null) executor.configNumber(raw, unit) else ReportValues.parseNumber(raw, unit)

  private fun present(row: ResultRow, field: String): Boolean = !row.values[field].isNullOrBlank()

  private fun rangeMessage(min: Double?, max: Double?): String = when {
    min != null && max != null -> "{value} liegt nicht zwischen {min} und {max}"
    min != null -> "{value} ist kleiner als {min}"
    else -> "{value} ist größer als {max}"
  }

  /** The result fields of [row] formatted like table cells, by field name. */
  private fun placeholders(result: QueryResult, row: ResultRow): Map<String, String> =
    result.columns.mapValues { (path, column) -> executor.text(column, row.values[path]) }

  /** What a row is about: its bucket, time value and group, as far as the result has them. */
  private fun label(timeField: String?, result: QueryResult, row: ResultRow): String? {
    val bucket = result.columns[ReportAggregate.BUCKET_FIELD]?.takeIf { it.bucket != null }
    val time = timeField?.let { result.columns[it] }?.takeIf { bucket == null }
    val group = result.groupBy?.let { result.columns[it] }
    return listOfNotNull(bucket, time, group).map { executor.text(it, row.values[it.path]) }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }
  }

  // endregion

  private fun warning(check: ReportCheck, label: String?, message: String) = WarningSpec(check.name, check.severity.key, label, message)

  private fun fill(message: String, values: Map<String, String>): String = ReportCheck.PLACEHOLDER.replace(message) { match -> values[match.groupValues[1]] ?: match.value }

  companion object {
    /** Platform cap for the periods one gap check walks through (about 55 years of days). */
    const val MAX_PERIODS = 20_000

    /** Platform cap for the days one calendar entry with `to` spans. */
    const val MAX_SPAN_DAYS = 366L

    private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    private const val GAP_MESSAGE = "Kein Eintrag"
    private const val REQUIRED_MESSAGE = "Fehlende Angabe: {missing}"
    private const val NO_VALUE = "–"
  }
}
