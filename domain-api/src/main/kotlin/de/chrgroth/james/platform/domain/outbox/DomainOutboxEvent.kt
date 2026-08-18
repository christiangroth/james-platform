package de.chrgroth.james.platform.domain.outbox

import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxEvent
import de.chrgroth.quarkus.outbox.domain.OutboxEventPriority

sealed interface DomainOutboxEvent : ApplicationOutboxEvent {
  override val partition: DomainOutboxPartition
  override val priority: OutboxEventPriority get() = OutboxEventPriority.MEDIUM
  override val serializePayload: String
}
