package de.chrgroth.james.platform.domain.port.out.infra

import de.chrgroth.james.platform.domain.model.infra.OutboxPartitionStats
import de.chrgroth.james.platform.domain.model.infra.OutboxTask
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent

interface OutboxPort {
  fun enqueue(event: DomainOutboxEvent)
  fun getPartitionStats(): List<OutboxPartitionStats>
  fun getTasksByPartition(partitionKey: String): List<OutboxTask>
}
