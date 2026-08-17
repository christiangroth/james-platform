package de.chrgroth.james.platform.adapter.out.outbox

import de.chrgroth.james.platform.domain.outbox.DomainOutboxPartition
import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxDispatcher
import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxEvent
import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxPartition
import de.chrgroth.quarkus.outbox.domain.DispatchResult
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class DomainOutboxTaskDispatcher : ApplicationOutboxDispatcher {

  override fun getAllPartitions(): List<ApplicationOutboxPartition> = DomainOutboxPartition.all

  override fun deserialize(partition: ApplicationOutboxPartition, eventType: String, payload: String): ApplicationOutboxEvent {
    throw IllegalArgumentException("Unknown outbox event type: $eventType")
  }

  override fun dispatch(event: ApplicationOutboxEvent): DispatchResult {
    logger.warn { "No handler available for outbox event type: ${event.key}" }
    return DispatchResult.Success
  }

  companion object : KLogging()
}
