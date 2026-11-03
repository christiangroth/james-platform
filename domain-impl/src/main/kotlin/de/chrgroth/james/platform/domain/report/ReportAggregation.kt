package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.ReportAggregate
import de.chrgroth.james.platform.domain.model.app.ReportAggregateFunction
import de.chrgroth.james.platform.domain.model.readmodel.encodeTimeBucket
import de.chrgroth.james.platform.domain.report.ReportExecutor.ResultRow
import java.time.LocalDate

/**
 * Aggregation of rows (see [ReportAggregate]) as a query's `aggregate` and the `aggregate` transformation step share it: rows grouped by time
 * bucket of their date and/or group value, one result row per bucket and group with the aggregated values in raw storage form.
 */
internal object ReportAggregation {

  /** One row to aggregate: its raw values by field, its [date] (for the bucket) and its [order] key (for LAST). */
  class Input(val values: Map<String, String?>, val date: LocalDate?, val order: String)

  /**
   * The aggregated rows (unsorted) of [inputs]; [columns] holds the result column of each aggregated value. Rows without date are left out when
   * the aggregate has a bucket, an empty group value is a group of its own (null).
   */
  fun rows(aggregate: ReportAggregate, columns: Map<String, ResultColumn>, inputs: List<Input>): List<ResultRow> {
    val grouped = inputs.mapNotNull { input ->
      val bucketKey = aggregate.bucket?.let { bucket -> encodeTimeBucket(bucket, input.date ?: return@mapNotNull null, aggregate.periodStart) }
      val groupKey = aggregate.groupBy?.let { input.values[it]?.takeIf { value -> value.isNotEmpty() } }
      Triple(bucketKey, groupKey, input)
    }.groupBy({ it.first to it.second }, { it.third })

    return grouped.map { (key, rows) ->
      val values = LinkedHashMap<String, String?>()
      if (aggregate.bucket != null) values[ReportAggregate.BUCKET_FIELD] = key.first
      aggregate.groupBy?.let { values[it] = key.second }
      aggregate.values.forEach { value -> values[value.name] = value(value.function, value.field, rows, columns[value.name]) }
      ResultRow(values, key.first ?: rows.maxOf { it.order })
    }
  }

  /** Aggregated rows in their natural order: by bucket, then by the group's display text ([label]). */
  fun sorted(rows: List<ResultRow>, groupColumn: ResultColumn?, label: (ResultColumn, String?) -> String): List<ResultRow> = rows.sortedWith(
    compareBy<ResultRow> { it.values[ReportAggregate.BUCKET_FIELD] ?: "" }
      .thenBy(String.CASE_INSENSITIVE_ORDER) { row -> groupColumn?.let { label(it, row.values[it.path]) } ?: "" },
  )

  private fun value(function: ReportAggregateFunction, field: String?, rows: List<Input>, column: ResultColumn?): String? {
    if (function == ReportAggregateFunction.COUNT) return (if (field == null) rows.size else rows.count { !it.values[field].isNullOrEmpty() }).toString()
    field ?: return null
    val contributions = rows.mapNotNull { row -> row.values[field]?.toDoubleOrNull()?.let { row to it } }
    if (contributions.isEmpty()) return null
    val numbers = contributions.map { it.second }
    val result = when (function) {
      ReportAggregateFunction.SUM -> numbers.sum()
      ReportAggregateFunction.AVG -> numbers.average()
      ReportAggregateFunction.MIN -> numbers.min()
      ReportAggregateFunction.MAX -> numbers.max()
      ReportAggregateFunction.LAST -> contributions.maxBy { it.first.order }.second
      ReportAggregateFunction.COUNT -> numbers.size.toDouble()
    }
    return ReportTransformer.rawNumber(result, column?.type)
  }
}
