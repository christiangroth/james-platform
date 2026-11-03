package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.ReportAggregate
import de.chrgroth.james.platform.domain.model.app.ReportCalcTransform
import de.chrgroth.james.platform.domain.model.app.ReportCumulativeTransform
import de.chrgroth.james.platform.domain.model.app.ReportDateRange
import de.chrgroth.james.platform.domain.model.app.ReportForecastTransform
import de.chrgroth.james.platform.domain.model.app.ReportMovingAverageTransform
import de.chrgroth.james.platform.domain.model.app.ReportQuery
import de.chrgroth.james.platform.domain.model.app.ReportResampleTransform
import de.chrgroth.james.platform.domain.model.app.ReportTransform
import de.chrgroth.james.platform.domain.model.app.ReportYoyTransform
import de.chrgroth.james.platform.domain.model.app.TimeBucket
import de.chrgroth.james.platform.domain.model.readmodel.encodeTimeBucket
import de.chrgroth.james.platform.domain.model.readmodel.nextTimeBucket
import de.chrgroth.james.platform.domain.report.ReportExecutor.QueryResult
import de.chrgroth.james.platform.domain.report.ReportExecutor.ResultRow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Applies a query's transformation steps (see [ReportTransform]) to its result. Running steps process the rows chronologically (by their
 * order key: bucket, time value or creation time) and per group of a grouped aggregate; the rows keep the order the query sorted them in.
 * [configNumber] answers the raw value of a `config.<field>` for calculated fields; [previousYear] runs the query (with the steps before the
 * given index) over its range shifted back one year, for year-over-year values.
 */
internal class ReportTransformer(
  private val today: LocalDate,
  private val configNumber: (String) -> Double?,
  private val previousYear: (stepIndex: Int) -> QueryResult?,
) {
  private val expressions = HashMap<String, ReportExpression?>()

  fun apply(query: ReportQuery, range: ReportDateRange?, steps: List<ReportTransform>, base: QueryResult): QueryResult =
    steps.foldIndexed(base) { index, result, step -> applyStep(query, range, index, step, result) }

  private fun applyStep(query: ReportQuery, range: ReportDateRange?, index: Int, step: ReportTransform, result: QueryResult): QueryResult = when (step) {
    is ReportCalcTransform -> calc(step, result)
    is ReportCumulativeTransform -> running(result, step.field, step.output, valueColumn(result, step.field, step.output, keepType = true)) { values ->
      var total = 0.0
      values.map { value ->
        total += value ?: 0.0
        total
      }
    }
    is ReportMovingAverageTransform -> running(result, step.field, step.output, valueColumn(result, step.field, step.output, keepType = false)) { values ->
      val window = ArrayDeque<Double>()
      values.map { value ->
        value?.let {
          window.addLast(it)
          if (window.size > step.window) window.removeFirst()
          window.average()
        }
      }
    }
    is ReportYoyTransform -> yoy(step, index, result)
    is ReportForecastTransform -> forecast(step, range, result)
    is ReportResampleTransform -> resample(query, step, result)
  }

  // region calc

  private fun calc(step: ReportCalcTransform, result: QueryResult): QueryResult {
    val expression = expressions.getOrPut(step.expression) { runCatching { ReportExpression.parse(step.expression) }.getOrNull() }
    val column = ResultColumn(step.name, ColumnRole.VALUE, PropertyType.DOUBLE, step.unit)
    val rows = result.rows.map { row ->
      val value = expression?.evaluate { name -> if (ReportFields.configPath(name) != null) configNumber(name) else row.values[name]?.toDoubleOrNull() }
      row.with(step.name, value?.toString())
    }
    return result.copy(columns = result.columns + (step.name to column), rows = rows)
  }

  // endregion

  // region running values

  private fun valueColumn(result: QueryResult, field: String, output: String, keepType: Boolean): ResultColumn {
    val source = result.columns[field]
    return ResultColumn(output, ColumnRole.VALUE, if (keepType) source?.type else PropertyType.DOUBLE, source?.unit)
  }

  /** Computes [output] from [field] over each group's rows in chronological order, [compute] mapping the values in that order. */
  private fun running(result: QueryResult, field: String, output: String, column: ResultColumn, compute: (List<Double?>) -> List<Double?>): QueryResult {
    val computed = HashMap<ResultRow, Double?>()
    chronologicalGroups(result).forEach { rows ->
      val values = compute(rows.map { it.values[field]?.toDoubleOrNull() })
      rows.forEachIndexed { i, row -> computed[row] = values[i] }
    }
    val rows = result.rows.map { row -> row.with(output, computed[row]?.let { rawNumber(it, column.type) }) }
    return result.copy(columns = result.columns + (output to column), rows = rows)
  }

  private fun chronologicalGroups(result: QueryResult): Collection<List<ResultRow>> =
    result.rows.groupBy { row -> result.groupBy?.let { row.values[it] } }.values.map { rows -> rows.sortedBy { it.order } }

  // endregion

  // region year over year

  private fun yoy(step: ReportYoyTransform, index: Int, result: QueryResult): QueryResult {
    val column = ResultColumn(step.output, ColumnRole.VALUE, result.columns[step.field]?.type, result.columns[step.field]?.unit)
    val bucket = result.bucket ?: return result.withEmpty(step.output, column)
    val previous = previousYear(index) ?: result
    val byKey = previous.rows.associateBy({ (it.values[ReportAggregate.BUCKET_FIELD] to previous.groupBy?.let { group -> it.values[group] }) }, { it.values[step.field] })
    val rows = result.rows.map { row ->
      val key = row.values[ReportAggregate.BUCKET_FIELD]?.let { ReportBuckets.previousYear(bucket, it, result.periodStart) }
      row.with(step.output, key?.let { byKey[it to result.groupBy?.let { group -> row.values[group] }] })
    }
    return result.copy(columns = result.columns + (step.output to column), rows = rows)
  }

  // endregion

  // region forecast

  private fun forecast(step: ReportForecastTransform, range: ReportDateRange?, result: QueryResult): QueryResult {
    val column = ResultColumn(step.output, ColumnRole.VALUE, PropertyType.DOUBLE, result.columns[step.field]?.unit)
    val bucket = result.bucket
    val rows = result.rows.map { row ->
      val period = if (bucket != null) {
        row.values[ReportAggregate.BUCKET_FIELD]?.let { ReportBuckets.period(bucket, it, result.periodStart) }
      } else {
        range?.takeIf { it.from != null && it.to != null }
      }
      val value = row.values[step.field]?.toDoubleOrNull()
      row.with(step.output, period?.let { extrapolate(value, it) }?.toString())
    }
    return result.copy(columns = result.columns + (step.output to column), rows = rows)
  }

  /** [value] of a period extrapolated to its end: completed periods keep it, future ones have none, the current one is scaled by elapsed days. */
  private fun extrapolate(value: Double?, period: ReportDateRange): Double? {
    val from = period.from!!
    val to = period.to!!
    return when {
      today.isAfter(to) -> value
      today.isBefore(from) -> null
      else -> (value ?: 0.0) * (ChronoUnit.DAYS.between(from, to) + 1) / (ChronoUnit.DAYS.between(from, today) + 1)
    }
  }

  // endregion

  // region resample

  private fun resample(query: ReportQuery, step: ReportResampleTransform, result: QueryResult): QueryResult {
    val dated = result.rows.mapNotNull { row -> dateOf(query, result, row)?.let { it to row } }
    val latestPerBucket = dated.groupBy { (date, _) -> encodeTimeBucket(step.bucket, date, step.periodStart) }
      .mapValues { (_, rows) -> rows.maxWith(compareBy<Pair<LocalDate, ResultRow>> { it.first }.thenBy { it.second.order }).second }
    val columns = LinkedHashMap<String, ResultColumn>()
    columns[ReportAggregate.BUCKET_FIELD] = ResultColumn(ReportAggregate.BUCKET_FIELD, ColumnRole.BUCKET, PropertyType.STRING, bucket = step.bucket)
    result.columns.filterKeys { it != ReportAggregate.BUCKET_FIELD }.forEach { (key, column) -> columns[key] = column }

    var carried: Map<String, String?> = emptyMap()
    val rows = consecutive(step.bucket, latestPerBucket.keys.sorted()).map { key ->
      val row = latestPerBucket[key]
      val values = when {
        row != null -> row.values
        step.fillForward -> carried
        else -> emptyMap()
      }.filterKeys { it != ReportAggregate.BUCKET_FIELD }
      carried = values
      ResultRow(linkedMapOf<String, String?>(ReportAggregate.BUCKET_FIELD to key) + values, key)
    }
    return QueryResult(columns, rows, step.bucket, step.periodStart, null)
  }

  /** The date a row stands for: the start of its bucket, else its time field's value. */
  private fun dateOf(query: ReportQuery, result: QueryResult, row: ResultRow): LocalDate? {
    result.bucket?.let { bucket -> return row.values[ReportAggregate.BUCKET_FIELD]?.let { ReportBuckets.period(bucket, it, result.periodStart)?.from } }
    val raw = query.timeField?.let { row.values[it] }?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { LocalDate.parse(raw) }.getOrNull() ?: runCatching { LocalDateTime.parse(raw).toLocalDate() }.getOrNull()
  }

  /** All bucket keys from the first to the last of [keys]; [keys] itself if that would exceed the platform's category cap. */
  private fun consecutive(bucket: TimeBucket, keys: List<String>): List<String> {
    if (keys.isEmpty()) return emptyList()
    val filled = mutableListOf(keys.first())
    while (filled.last() < keys.last()) {
      val next = nextTimeBucket(bucket, filled.last()) ?: return keys
      if (filled.size >= ReportExecutor.MAX_CATEGORIES) return keys
      filled += next
    }
    return filled
  }

  // endregion

  private fun ResultRow.with(field: String, raw: String?): ResultRow = ResultRow(LinkedHashMap(values).apply { put(field, raw) }, order)

  private fun QueryResult.withEmpty(field: String, column: ResultColumn): QueryResult =
    copy(columns = columns + (field to column), rows = rows.map { it.with(field, null) })

  companion object {
    fun rawNumber(value: Double, type: PropertyType?): String =
      if (type == PropertyType.LONG && value == Math.rint(value)) value.toLong().toString() else value.toString()
  }
}
