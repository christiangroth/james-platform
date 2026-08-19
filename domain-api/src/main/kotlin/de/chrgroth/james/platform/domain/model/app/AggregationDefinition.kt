package de.chrgroth.james.platform.domain.model.app

@JvmInline
value class AggregationDefinitionId(val value: String)

/**
 * Function applied to [AggregationDefinition.sourceProperty] across the aggregated [AppData] instances. No
 * percentiles: unlike SUM/COUNT/AVG/MIN/MAX, a true percentile cannot be maintained incrementally without either
 * storing the full underlying sample set or an approximating structure — deliberately out of scope for the first
 * iteration, see docs/adr/0020-aggregation-definitions.md.
 */
enum class AggregationFunction {
  SUM,
  COUNT,
  AVG,
  MIN,
  MAX,
  ;

  /** SUM/AVG/MIN/MAX only make sense on a numeric source property; COUNT counts non-null occurrences of any type. */
  fun requiresNumericSourceProperty(): Boolean = this != COUNT
}

/** Time bucket an aggregation's values are additionally grouped by, e.g. SUM per MONAT. */
enum class TimeBucket { TAG, WOCHE, MONAT, JAHR }

/**
 * Declares a precomputed rollup over instances of the [EntityDefinition] this is part of (analogous to
 * [ComputedProperty]). [sourceProperty] and [groupBy], if set, must be top-level properties of that same Entity.
 *
 * [refPath], if set, must be a top-level property of that same Entity of type [PropertyType.REF] (Ref-depth 1, see
 * docs/adr/0020-aggregation-definitions.md) — it groups the aggregation's values per instance of the referenced
 * Entity, e.g. `Lauf.laufschuhId` producing one value per `Laufschuh`. Without [refPath], the aggregation produces
 * a single, ungrouped value across all instances of the owning Entity.
 */
data class AggregationDefinition(
  val id: AggregationDefinitionId,
  val name: String,
  val function: AggregationFunction,
  val sourceProperty: PropertyId,
  val refPath: PropertyId? = null,
  val timeBucket: TimeBucket? = null,
  val groupBy: PropertyId? = null,
)
