package de.chrgroth.james.platform.domain.model.imports

enum class FilterMode {
  INCLUDE,
  EXCLUDE,
}

enum class FilterOperator {
  IS_NULL,
  IS_NOT_NULL,
  EQUALS,
  NOT_EQUALS,
  CONTAINS,
  MATCHES,
  NOT_MATCHES,
  GREATER_THAN,
  GREATER_THAN_OR_EQUAL,
  LESS_THAN,
  LESS_THAN_OR_EQUAL,
}

private val TEXT_ONLY_OPERATORS = setOf(FilterOperator.MATCHES, FilterOperator.NOT_MATCHES)
private val COMPARISON_OPERATORS = setOf(
  FilterOperator.GREATER_THAN,
  FilterOperator.GREATER_THAN_OR_EQUAL,
  FilterOperator.LESS_THAN,
  FilterOperator.LESS_THAN_OR_EQUAL,
)
private val COMPARABLE_SCHEMA_TYPES = setOf(SchemaPropertyType.LONG, SchemaPropertyType.DOUBLE, SchemaPropertyType.DATE, SchemaPropertyType.DATETIME)

/**
 * The schema property types this operator is offered for in the filter UI. An empty set means the operator applies
 * to every type (e.g. [FilterOperator.EQUALS]). This is a UI-only restriction - [FilterEvaluator] itself does not
 * enforce it.
 */
fun FilterOperator.applicableSchemaTypes(): Set<SchemaPropertyType> = when (this) {
  in TEXT_ONLY_OPERATORS -> setOf(SchemaPropertyType.STRING)
  in COMPARISON_OPERATORS -> COMPARABLE_SCHEMA_TYPES
  else -> emptySet()
}

/**
 * A single filter rule evaluated against a source record's [sourcePath] (a path into the detected schema). Rules of
 * an [ImportJob.filterRules] list are applied in order as a pipeline: an [FilterMode.INCLUDE] rule narrows the
 * current record set down to matches, an [FilterMode.EXCLUDE] rule removes matches from it - so include and exclude
 * rules can be freely mixed, and each rule only sees the records that survived every rule before it. [value] is
 * unused for [FilterOperator.IS_NULL] and [FilterOperator.IS_NOT_NULL].
 */
data class FilterRule(
  val mode: FilterMode,
  val sourcePath: String,
  val operator: FilterOperator,
  val value: String? = null,
)

/** Bundles everything the filter UI needs to render: the import job and how many of its source records currently match the configured [ImportJob.filterRules]. */
data class FilterView(
  val importJob: ImportJob,
  val totalRecordCount: Int,
  val matchingRecordCount: Int,
)
