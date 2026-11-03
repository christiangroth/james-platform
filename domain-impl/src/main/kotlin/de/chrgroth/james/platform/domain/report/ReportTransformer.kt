package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.ReportAggregate
import de.chrgroth.james.platform.domain.model.app.ReportAsOfTransform
import de.chrgroth.james.platform.domain.model.app.ReportCalcTransform
import de.chrgroth.james.platform.domain.model.app.ReportCumulativeTransform
import de.chrgroth.james.platform.domain.model.app.ReportDateRange
import de.chrgroth.james.platform.domain.model.app.ReportDeltaTransform
import de.chrgroth.james.platform.domain.model.app.ReportForecastTransform
import de.chrgroth.james.platform.domain.model.app.ReportGoalTransform
import de.chrgroth.james.platform.domain.model.app.ReportJoinKey
import de.chrgroth.james.platform.domain.model.app.ReportJoinTransform
import de.chrgroth.james.platform.domain.model.app.ReportMovingAverageTransform
import de.chrgroth.james.platform.domain.model.app.ReportQuery
import de.chrgroth.james.platform.domain.model.app.ReportResampleTransform
import de.chrgroth.james.platform.domain.model.app.ReportTopTransform
import de.chrgroth.james.platform.domain.model.app.ReportTransform
import de.chrgroth.james.platform.domain.model.app.ReportYoyTransform
import de.chrgroth.james.platform.domain.model.app.TimeBucket
import de.chrgroth.james.platform.domain.model.readmodel.encodeTimeBucket
import de.chrgroth.james.platform.domain.model.readmodel.nextTimeBucket
import de.chrgroth.james.platform.domain.report.ReportExecutor.QueryResult
import de.chrgroth.james.platform.domain.report.ReportExecutor.ResultRow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.temporal.ChronoUnit

/**
 * Applies a query's transformation steps (see [ReportTransform]) to its result. Running steps process the rows chronologically (by their
 * order key: bucket, time value or creation time) and per group of a grouped aggregate; the rows keep the order the query sorted them in.
 * [configNumber] answers the value of a `config.<field>` (raw, or converted into the given unit) for calculated fields and goals; [previousYear]
 * runs the query (with the steps before the given index) over its range shifted back one year, for year-over-year values; [otherQuery] answers
 * another query's transformed result for `asOf` and `join`.
 */
internal class ReportTransformer(
  private val today: LocalDate,
  private val configNumber: (raw: String, unit: PropertyUnit?) -> Double?,
  private val previousYear: (stepIndex: Int) -> QueryResult?,
  private val otherQuery: (name: String) -> Pair<ReportQuery, QueryResult>? = { null },
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
    is ReportDeltaTransform -> step.bucket?.let { distributedDelta(query, step, it, result) } ?: delta(query, step, result)
    is ReportAsOfTransform -> asOf(query, step, result)
    is ReportJoinTransform -> join(step, result)
    is ReportTopTransform -> top(query, step, result)
    is ReportGoalTransform -> goal(query, step, result)
  }

  // region calc

  private fun calc(step: ReportCalcTransform, result: QueryResult): QueryResult {
    val expression = expressions.getOrPut(step.expression) { runCatching { ReportExpression.parse(step.expression) }.getOrNull() }
    val column = ResultColumn(step.name, ColumnRole.VALUE, PropertyType.DOUBLE, step.unit)
    val rows = result.rows.map { row ->
      val value = expression?.evaluate { name -> if (ReportFields.configPath(name) != null) configNumber(name, null) else row.values[name]?.toDoubleOrNull() }
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

  // region delta

  /** One reading of a delta: the row, its date and value. */
  private class Reading(val row: ResultRow, val date: LocalDate, val value: Double)

  /** Readings per group (the grouped aggregate's group or the `by` field), chronologically; rows without date or value are left out. */
  private fun readings(query: ReportQuery, result: QueryResult, field: String, by: String?): Map<String?, List<Reading>> {
    val groupField = by ?: result.groupBy
    return result.rows.mapNotNull { row ->
      val date = dateOf(query, result, row) ?: return@mapNotNull null
      val value = row.values[field]?.toDoubleOrNull() ?: return@mapNotNull null
      Reading(row, date, value)
    }.groupBy { reading -> groupField?.let { reading.row.values[it] } }
      .mapValues { (_, readings) -> readings.sortedWith(compareBy<Reading> { it.date }.thenBy { it.row.order }) }
  }

  private fun delta(query: ReportQuery, step: ReportDeltaTransform, result: QueryResult): QueryResult {
    val source = result.columns[step.field]
    val column = ResultColumn(step.output, ColumnRole.VALUE, if (step.perDay) PropertyType.DOUBLE else source?.type, source?.unit)
    val computed = HashMap<ResultRow, Double?>()
    readings(query, result, step.field, step.by).values.forEach { readings ->
      readings.zipWithNext().forEach { (previous, current) ->
        val difference = current.value - previous.value
        computed[current.row] = if (step.perDay) {
          ChronoUnit.DAYS.between(previous.date, current.date).takeIf { it > 0 }?.let { difference / it }
        } else {
          difference
        }
      }
    }
    val rows = result.rows.map { row -> row.with(step.output, computed[row]?.let { rawNumber(it, column.type) }) }
    return result.copy(columns = result.columns + (step.output to column), rows = rows)
  }

  /**
   * The differences between consecutive readings spread evenly over the days from one reading up to the next (the reading's own day belongs to
   * the next interval) and summed per bucket and group; a difference between readings of the same day counts for that day's bucket.
   */
  private fun distributedDelta(query: ReportQuery, step: ReportDeltaTransform, bucket: TimeBucket, result: QueryResult): QueryResult {
    val groupField = step.by ?: result.groupBy
    val sums = LinkedHashMap<Pair<String, String?>, Double>()
    readings(query, result, step.field, step.by).forEach { (group, readings) ->
      readings.zipWithNext().forEach { (previous, current) ->
        val difference = current.value - previous.value
        val days = ChronoUnit.DAYS.between(previous.date, current.date)
        if (days == 0L) {
          sums.merge(encodeTimeBucket(bucket, current.date, step.periodStart) to group, difference, Double::plus)
          return@forEach
        }
        var cursor = previous.date
        while (cursor.isBefore(current.date)) {
          val key = encodeTimeBucket(bucket, cursor, step.periodStart)
          val periodEnd = ReportBuckets.period(bucket, key, step.periodStart)?.to ?: cursor
          val segmentEnd = minOf(periodEnd.plusDays(1), current.date)
          sums.merge(key to group, difference * ChronoUnit.DAYS.between(cursor, segmentEnd) / days, Double::plus)
          cursor = segmentEnd
        }
      }
    }
    val columns = LinkedHashMap<String, ResultColumn>()
    columns[ReportAggregate.BUCKET_FIELD] = ResultColumn(ReportAggregate.BUCKET_FIELD, ColumnRole.BUCKET, PropertyType.STRING, bucket = bucket)
    groupField?.let { field -> result.columns[field]?.let { columns[field] = it.copy(role = ColumnRole.GROUP) } }
    columns[step.output] = ResultColumn(step.output, ColumnRole.VALUE, PropertyType.DOUBLE, result.columns[step.field]?.unit)
    val rows = sums.entries.sortedWith(compareBy<Map.Entry<Pair<String, String?>, Double>> { it.key.first }.thenBy { it.key.second.orEmpty() })
      .map { (key, sum) ->
        val values = linkedMapOf<String, String?>(ReportAggregate.BUCKET_FIELD to key.first)
        groupField?.let { values[it] = key.second }
        values[step.output] = sum.toString()
        ResultRow(values, key.first)
      }
    return QueryResult(columns, rows, bucket, step.periodStart, groupField)
  }

  // endregion

  // region as-of and join

  private fun attachedColumns(other: QueryResult?, fields: List<String>, prefix: String): Map<String, ResultColumn> = fields.associate { field ->
    val name = prefix + field
    name to (other?.columns?.get(field)?.copy(path = name, role = ColumnRole.FIELD, bucket = null) ?: ResultColumn(name, ColumnRole.FIELD, null))
  }

  private fun ResultRow.attach(match: ResultRow?, fields: List<String>, prefix: String): ResultRow =
    ResultRow(LinkedHashMap(values).apply { fields.forEach { put(prefix + it, match?.values?.get(it)) } }, order, id)

  /** The key a row is matched by: a field's value, or - for [ReportJoinKey.OBJECT_ID] without such a field - the row's object id. */
  private fun keyOf(row: ResultRow, field: String, columns: Map<String, ResultColumn>): String? =
    if (field == ReportJoinKey.OBJECT_ID && field !in columns) row.id else row.values[field]?.takeIf { it.isNotEmpty() }

  private fun asOf(query: ReportQuery, step: ReportAsOfTransform, result: QueryResult): QueryResult {
    val other = otherQuery(step.query)
    val columns = result.columns + attachedColumns(other?.second, step.fields, step.prefix)
    val candidates = other?.let { (otherDefinition, otherResult) ->
      otherResult.rows.mapNotNull { row -> dateOf(otherDefinition, otherResult, row)?.let { it to row } }
        .groupBy { (_, row) -> step.on?.let { keyOf(row, it.other, otherResult.columns) } }
        .mapValues { (_, rows) -> rows.sortedWith(compareBy<Pair<LocalDate, ResultRow>> { it.first }.thenBy { it.second.order }) }
    }.orEmpty()
    val rows = result.rows.map { row ->
      val date = dateOf(query, result, row, step.atEnd)
      val pool = candidates[step.on?.let { keyOf(row, it.field, result.columns) }]
      val match = date?.let { d -> pool?.lastOrNull { !it.first.isAfter(d) }?.second }
      row.attach(match, step.fields, step.prefix)
    }
    return result.copy(columns = columns, rows = rows)
  }

  private fun join(step: ReportJoinTransform, result: QueryResult): QueryResult {
    val other = otherQuery(step.query)?.second
    val columns = result.columns + attachedColumns(other, step.fields, step.prefix)
    val index = HashMap<String, ResultRow>()
    other?.rows?.forEach { row -> keyOf(row, step.on.other, other.columns)?.let { index.putIfAbsent(it, row) } }
    val rows = result.rows.map { row -> row.attach(keyOf(row, step.on.field, result.columns)?.let { index[it] }, step.fields, step.prefix) }
    return result.copy(columns = columns, rows = rows)
  }

  // endregion

  // region top

  private class Candidate(val bucket: String?, val group: String?, val row: ResultRow, val value: Double)

  private fun top(query: ReportQuery, step: ReportTopTransform, result: QueryResult): QueryResult {
    val bucket = step.bucket
    val candidates = result.rows.mapNotNull { row ->
      val value = row.values[step.field]?.toDoubleOrNull() ?: return@mapNotNull null
      val key = if (bucket == null) null else dateOf(query, result, row)?.let { encodeTimeBucket(bucket, it, step.periodStart) } ?: return@mapNotNull null
      Candidate(key, step.by?.let { row.values[it] }, row, value)
    }
    val partitions = candidates.groupBy { it.bucket.orEmpty() to it.group.orEmpty() }.entries.sortedWith(compareBy({ it.key.first }, { it.key.second }))
    val rows = partitions.flatMap { (_, partition) ->
      val sorted = if (step.ascending) partition.sortedBy { it.value } else partition.sortedByDescending { it.value }
      sorted.take(step.n).mapIndexed { index, candidate ->
        val values = LinkedHashMap<String, String?>()
        if (bucket != null) values[ReportAggregate.BUCKET_FIELD] = candidate.bucket
        values.putAll(candidate.row.values.filterKeys { bucket == null || it != ReportAggregate.BUCKET_FIELD })
        values[step.output] = (index + 1).toString()
        ResultRow(values, candidate.row.order, candidate.row.id)
      }
    }
    val columns = LinkedHashMap<String, ResultColumn>()
    if (bucket != null) columns[ReportAggregate.BUCKET_FIELD] = ResultColumn(ReportAggregate.BUCKET_FIELD, ColumnRole.BUCKET, PropertyType.STRING, bucket = bucket)
    columns.putAll(result.columns.filterKeys { bucket == null || it != ReportAggregate.BUCKET_FIELD })
    columns[step.output] = ResultColumn(step.output, ColumnRole.VALUE, PropertyType.LONG)
    return result.copy(columns = columns, rows = rows)
  }

  // endregion

  // region goal

  private fun goal(query: ReportQuery, step: ReportGoalTransform, result: QueryResult): QueryResult {
    val unit = result.columns[step.field]?.unit
    val constant = if (ReportFields.configPath(step.target) != null) configNumber(step.target, unit) else ReportValues.parseNumber(step.target, unit)
    val targetColumn = if (constant == null) result.columns[step.target.trim()] else null
    val rows = result.rows.map { row ->
      val full = constant ?: targetColumn?.let { column -> row.values[column.path]?.toDoubleOrNull()?.let { ReportFields.convertUnit(it, column.unit, unit) } }
      val share = step.period?.let { proRata(query, result, row, it, step.periodStart) } ?: 1.0
      val target = full?.let { it * share }
      val actual = row.values[step.field]?.toDoubleOrNull()
      val progress = if (target != null && actual != null && target != 0.0) actual / target * PERCENT else null
      row.with(step.output, target?.toString()).with(step.progress, progress?.toString())
    }
    val columns = result.columns + (step.output to ResultColumn(step.output, ColumnRole.VALUE, PropertyType.DOUBLE, unit)) +
      (step.progress to ResultColumn(step.progress, ColumnRole.VALUE, PropertyType.DOUBLE))
    return result.copy(columns = columns, rows = rows)
  }

  /** The share of [period] elapsed up to the row's date (the end of its bucket), between 0 and 1; null without date (the full target applies). */
  private fun proRata(query: ReportQuery, result: QueryResult, row: ResultRow, period: TimeBucket, periodStart: MonthDay?): Double? {
    val date = dateOf(query, result, row, atEnd = true) ?: return null
    val range = ReportBuckets.period(period, encodeTimeBucket(period, date, periodStart), periodStart) ?: return null
    val total = ChronoUnit.DAYS.between(range.from, range.to) + 1
    return (ChronoUnit.DAYS.between(range.from, date) + 1).toDouble() / total
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

  /** The date a row stands for: the start (with [atEnd] the end) of its bucket, else its time field's value. */
  private fun dateOf(query: ReportQuery, result: QueryResult, row: ResultRow, atEnd: Boolean = false): LocalDate? {
    result.bucket?.let { bucket ->
      val period = row.values[ReportAggregate.BUCKET_FIELD]?.let { ReportBuckets.period(bucket, it, result.periodStart) }
      return if (atEnd) period?.to else period?.from
    }
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

  private fun ResultRow.with(field: String, raw: String?): ResultRow = ResultRow(LinkedHashMap(values).apply { put(field, raw) }, order, id)

  private fun QueryResult.withEmpty(field: String, column: ResultColumn): QueryResult =
    copy(columns = columns + (field to column), rows = rows.map { it.with(field, null) })

  companion object {
    private const val PERCENT = 100.0

    fun rawNumber(value: Double, type: PropertyType?): String =
      if (type == PropertyType.LONG && value == Math.rint(value)) value.toLong().toString() else value.toString()
  }
}
