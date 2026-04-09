package de.chrgroth.spotify.control.domain.model.infra

import kotlin.time.Instant

data class OutboxPartitionStats(
  val name: String,
  val status: String,
  val documentCount: Long,
  val blockedUntil: Instant?,
  val eventTypeCounts: List<OutboxEventTypeCount> = emptyList(),
)
