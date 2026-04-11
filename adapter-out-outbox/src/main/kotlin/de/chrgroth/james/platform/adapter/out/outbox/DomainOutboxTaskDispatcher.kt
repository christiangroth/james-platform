package de.chrgroth.james.platform.adapter.out.outbox

import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
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

  override fun deserialize(partition: ApplicationOutboxPartition, eventType: String, payload: String): ApplicationOutboxEvent =
    DomainOutboxEvent.fromKey(eventType, payload)

  override fun dispatch(event: ApplicationOutboxEvent): DispatchResult {
    if (event !is DomainOutboxEvent) {
      logger.error { "Unknown outbox event type: ${event::class.qualifiedName}" }
      return DispatchResult.Failed("Unknown event type: ${event::class.qualifiedName}")
    }
    logger.warn { "No handler available for outbox event type: ${event.key}" }
    return DispatchResult.Success
  }

  companion object : KLogging()
}
