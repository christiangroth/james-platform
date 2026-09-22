package de.chrgroth.james.platform.domain.port.out.infra

import de.chrgroth.james.platform.domain.model.infra.OutboxPartitionStats
import de.chrgroth.james.platform.domain.model.infra.OutboxTask
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import de.chrgroth.james.platform.domain.outbox.DomainOutboxPartition
import java.time.Instant

interface OutboxPort {
  /** Enqueues [event] for immediate dispatch, or - when [notBefore] is given - delays it until at or after that instant (see ADR 0019's delayed-dispatch section). */
  fun enqueue(event: DomainOutboxEvent, notBefore: Instant? = null)

  /** Cancels the still-pending task identified by [deduplicationKey] within [partition], if any; a no-op if nothing matches. */
  fun cancel(partition: DomainOutboxPartition, deduplicationKey: String)

  /** Moves the still-pending task identified by [deduplicationKey] within [partition] to fire at [notBefore] instead of its previously scheduled time. */
  fun reschedule(partition: DomainOutboxPartition, deduplicationKey: String, notBefore: Instant)

  fun getPartitionStats(): List<OutboxPartitionStats>
  fun getTasksByPartition(partitionKey: String): List<OutboxTask>
}
