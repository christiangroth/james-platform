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
