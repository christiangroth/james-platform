package de.chrgroth.spotify.control.domain.model.infra

/**
 * Stats for a tracked MongoDB query operation within a 24-hour sliding window.
 *
 * @property name the name of the tracked operation (e.g. "user.findById")
 * @property executionCountLast24h number of times this operation was executed in the last 24 hours
 * @property slowQueryCount number of executions in the last 24 hours that exceeded the configured
 *   slow-query threshold (see `app.mongodb.slow-query-threshold-ms`)
 */
data class MongoQueryStats(
  val name: String,
  val executionCountLast24h: Long,
  val slowQueryCount: Long,
)
