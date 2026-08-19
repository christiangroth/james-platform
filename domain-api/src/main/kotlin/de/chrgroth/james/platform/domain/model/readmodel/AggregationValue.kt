package de.chrgroth.james.platform.domain.model.readmodel

import de.chrgroth.james.platform.domain.model.app.AggregationDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import java.time.Instant

/** See docs/adr/0013-precomputed-read-models-per-ui-page.md — mirrors that ADR's read-model status convention. */
enum class AggregationValueStatus { UP_TO_DATE, STALE }

data class AggregationValueId(
  val installedAppId: InstalledAppId,
  val aggregationDefinitionId: AggregationDefinitionId,
  // AppData id of the referenced Entity instance this value is grouped by (via AggregationDefinition.refPath), or
  // null for an ungrouped, installation-wide value.
  val groupKey: String? = null,
  // Encoded time bucket period (e.g. "2026-08" for MONAT), or null when the aggregation has no time bucketing.
  val bucketKey: String? = null,
)

data class AggregationValue(
  val id: AggregationValueId,
  val value: Double,
  val status: AggregationValueStatus,
  val updatedAt: Instant,
  // Number of AppData instances contributing to `value`. Only needed to incrementally recompute AVG's running mean
  // (see AggregationComputation.kt); left at 0 for every other AggregationFunction.
  val sampleCount: Long = 0,
)
