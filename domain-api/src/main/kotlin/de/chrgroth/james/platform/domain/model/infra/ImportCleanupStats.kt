package de.chrgroth.james.platform.domain.model.infra

import kotlin.time.Instant

/**
 * Collected execution metrics for the import document cleanup cronjob.
 *
 * @property executionCount total number of times the cleanup job has run since application start
 * @property deletedCount total number of stale import documents deleted since application start
 * @property errorCount total number of runs that ended in an exception
 * @property totalDurationMs cumulative wall-clock time (ms) spent running the cleanup job
 * @property lastRunAt timestamp of the most recent run, or null if it has not run yet
 */
data class ImportCleanupStats(
  val executionCount: Long,
  val deletedCount: Long,
  val errorCount: Long,
  val totalDurationMs: Long,
  val lastRunAt: Instant?,
)
