package de.chrgroth.spotify.control.domain.model.infra

data class OutboxEventTypeCount(
  val eventType: String,
  val count: Long,
)
