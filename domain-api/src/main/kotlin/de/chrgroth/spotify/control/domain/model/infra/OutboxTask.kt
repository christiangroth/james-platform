package de.chrgroth.spotify.control.domain.model.infra

import kotlin.time.Instant

data class OutboxTask(
  val eventType: String,
  val deduplicationKey: String,
  val priority: String,
  val status: String,
  val attempts: Int,
  val nextRetryAt: Instant?,
  val createdAt: Instant,
  val lastError: String?,
) {
  val priorityOrder: Int get() = if (priority == "HIGH") 0 else 1
  val isHighPriority: Boolean get() = priority == "HIGH"
}
