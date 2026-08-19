package de.chrgroth.james.platform.domain.model.readmodel

import de.chrgroth.james.platform.domain.model.app.AggregationDefinition
import de.chrgroth.james.platform.domain.model.app.AggregationFunction
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.TimeBucket
import java.time.Instant
import java.time.ZoneOffset
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
  val raw = item.data[sourceProperty.value]
  return if (function == AggregationFunction.COUNT) {
    if (raw.isNullOrBlank()) null else 1.0
  } else {
    raw?.toDoubleOrNull()
  }
}

/**
 * The group this aggregation's value for [item] belongs to: the AppData id of the referenced Entity instance
 * (via [AggregationDefinition.refPath]) if set, else the stringified value of [AggregationDefinition.groupBy] if
 * set, else null for a single, ungrouped value across the owning Entity.
 */
fun AggregationDefinition.groupKeyOf(item: AppData): String? =
  refPath?.let { item.data[it.value] } ?: groupBy?.let { item.data[it.value] }

/** The time bucket [item] falls into, keyed by [AppData.createdAt], or null when [AggregationDefinition.timeBucket] is unset. */
fun AggregationDefinition.bucketKeyOf(item: AppData): String? = timeBucket?.let { encodeTimeBucket(it, item.createdAt) }

private fun encodeTimeBucket(bucket: TimeBucket, instant: Instant): String {
  val date = instant.atZone(ZoneOffset.UTC).toLocalDate()
  return when (bucket) {
    TimeBucket.TAG -> date.toString()
    TimeBucket.WOCHE -> {
      val weekFields = WeekFields.ISO
      "%04d-W%02d".format(date.get(weekFields.weekBasedYear()), date.get(weekFields.weekOfWeekBasedYear()))
    }
    TimeBucket.MONAT -> "%04d-%02d".format(date.year, date.monthValue)
    TimeBucket.JAHR -> date.year.toString()
  }
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
