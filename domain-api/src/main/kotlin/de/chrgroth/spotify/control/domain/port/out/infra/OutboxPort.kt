package de.chrgroth.spotify.control.domain.port.out.infra

import de.chrgroth.spotify.control.domain.model.infra.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.model.infra.OutboxTask
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface OutboxPort {
  fun enqueue(event: DomainOutboxEvent)
  fun getPartitionStats(): List<OutboxPartitionStats>
  fun getTasksByPartition(partitionKey: String): List<OutboxTask>
}
