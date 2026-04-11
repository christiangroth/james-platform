package de.chrgroth.james.platform.adapter.out.outbox

import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import de.chrgroth.james.platform.domain.outbox.DomainOutboxPartition
import de.chrgroth.james.platform.domain.port.`in`.playback.PlaybackAggregationPort
import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxDispatcher
import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxEvent
import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxPartition
import de.chrgroth.quarkus.outbox.domain.DispatchResult
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class DomainOutboxTaskDispatcher(
  private val playbackAggregation: PlaybackAggregationPort,
) : ApplicationOutboxDispatcher {

  override fun getAllPartitions(): List<ApplicationOutboxPartition> = DomainOutboxPartition.all

  override fun deserialize(partition: ApplicationOutboxPartition, eventType: String, payload: String): ApplicationOutboxEvent =
    DomainOutboxEvent.fromKey(eventType, payload)

  override fun dispatch(event: ApplicationOutboxEvent): DispatchResult {
    if (event !is DomainOutboxEvent) {
      logger.error { "Unknown outbox event type: ${event::class.qualifiedName}" }
      return DispatchResult.Failed("Unknown event type: ${event::class.qualifiedName}")
    }
    return when (event) {
      is DomainOutboxEvent.AggregatePlaybackData -> playbackAggregation.handle(event).fold(
        { error -> DispatchResult.Failed(error.code) },
        { DispatchResult.Success },
      )
      else -> {
        logger.warn { "No handler available for outbox event type: ${event.key}" }
        DispatchResult.Success
      }
    }
  }

  companion object : KLogging()
}
