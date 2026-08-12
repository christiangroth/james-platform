package de.chrgroth.james.platform.domain.model.infra

/**
 * Stats for a tracked HTTP response operation within a 24-hour sliding window.
 *
 * @property name the name of the tracked operation (e.g. "page.health.view")
 * @property executionCountLast24h number of times this operation was executed in the last 24 hours
 * @property slowResponseCount number of executions in the last 24 hours that exceeded the configured
 *   slow-response threshold (see `app.web.slow-response-threshold-ms`)
 */
data class HttpResponseStats(
  val name: String,
  val executionCountLast24h: Long,
  val slowResponseCount: Long,
)
