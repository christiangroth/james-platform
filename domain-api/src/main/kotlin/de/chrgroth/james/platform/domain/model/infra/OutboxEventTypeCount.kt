package de.chrgroth.james.platform.domain.model.infra

data class OutboxEventTypeCount(
  val eventType: String,
  val count: Long,
)
