package de.chrgroth.james.platform.domain.model.infra

/**
 * Classification of Kotlin script type tracked for health metrics.
 */
enum class ScriptType {
  SMART_DEFAULT,
  COMPUTED_PROPERTY,
  MIGRATION,
}

/**
 * Collected execution metrics for a single Kotlin script, identified by its type, entity and property.
 *
 * @property type the kind of script (smart default, computed property or migration)
 * @property entityName the display name of the entity owning the property
 * @property propertyName the display name of the property whose script was evaluated
 * @property executionCount total number of times this script has been evaluated since application start
 * @property errorCount total number of evaluations that ended in an exception
 * @property totalDurationMs cumulative wall-clock time (ms) spent evaluating this script
 */
data class ScriptExecutionStats(
  val type: ScriptType,
  val entityName: String,
  val propertyName: String,
  val executionCount: Long,
  val errorCount: Long,
  val totalDurationMs: Long,
)
