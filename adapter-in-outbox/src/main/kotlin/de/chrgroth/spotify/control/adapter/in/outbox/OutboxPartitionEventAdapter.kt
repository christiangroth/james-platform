package de.chrgroth.spotify.control.adapter.`in`.outbox

import de.chrgroth.quarkus.outbox.domain.event.OutboxPartitionActivatedEvent
import de.chrgroth.quarkus.outbox.domain.event.OutboxPartitionPausedEvent
import de.chrgroth.quarkus.outbox.domain.event.OutboxTaskDispatchedEvent
import de.chrgroth.quarkus.outbox.domain.event.OutboxTaskEnqueuedEvent
import de.chrgroth.quarkus.outbox.domain.event.OutboxTaskFailedEvent
import de.chrgroth.quarkus.outbox.domain.event.OutboxTaskRescheduledEvent
import de.chrgroth.quarkus.outbox.domain.event.OutboxTaskRetryScheduledEvent
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPartitionObserver
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxTaskCountObserver
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance

@ApplicationScoped
@Suppress("Unused")
class OutboxPartitionEventAdapter(
  @param:Any private val partitionObservers: Instance<OutboxPartitionObserver>,
  @param:Any private val taskCountObservers: Instance<OutboxTaskCountObserver>,
) {

  fun onPartitionPaused(@Observes event: OutboxPartitionPausedEvent) {
    val reason = event.reason?.takeIf { it.isNotBlank() } ?: "unknown"
    partitionObservers.forEach { it.onPartitionPaused(event.partition.key, reason) }
  }

  fun onPartitionActivated(@Observes event: OutboxPartitionActivatedEvent) {
    partitionObservers.forEach { it.onPartitionActivated(event.partition.key) }
  }

  @Suppress("UnusedParameter")
  fun onTaskEnqueued(@Observes event: OutboxTaskEnqueuedEvent) {
    taskCountObservers.forEach { it.onOutboxTaskCountChanged() }
  }

  @Suppress("UnusedParameter")
  fun onTaskDispatched(@Observes event: OutboxTaskDispatchedEvent) {
    taskCountObservers.forEach { it.onOutboxTaskCountChanged() }
  }

  @Suppress("UnusedParameter")
  fun onTaskFailed(@Observes event: OutboxTaskFailedEvent) {
    taskCountObservers.forEach { it.onOutboxTaskCountChanged() }
  }

  @Suppress("UnusedParameter")
  fun onTaskRescheduled(@Observes event: OutboxTaskRescheduledEvent) {
    taskCountObservers.forEach { it.onOutboxTaskCountChanged() }
  }

  @Suppress("UnusedParameter")
  fun onTaskRetryScheduled(@Observes event: OutboxTaskRetryScheduledEvent) {
    taskCountObservers.forEach { it.onOutboxTaskCountChanged() }
  }
}
