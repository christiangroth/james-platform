package de.chrgroth.spotify.control.adapter.out.outbox

import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxClient
import de.chrgroth.quarkus.outbox.domain.OutboxPartitionStatus
import de.chrgroth.spotify.control.domain.model.infra.OutboxEventTypeCount
import de.chrgroth.spotify.control.domain.model.infra.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.model.infra.OutboxTask
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import kotlin.time.toKotlinInstant

@ApplicationScoped
@Suppress("Unused")
class OutboxPortAdapter(
  private val outbox: ApplicationOutboxClient,
) : OutboxPort {

  override fun enqueue(event: DomainOutboxEvent) {
    outbox.enqueue(event)
    logger.info { "Enqueued outbox event ${event.key} in partition ${event.partition.key}" }
  }

  override fun getPartitionStats(): List<OutboxPartitionStats> {
    val partitionInfosByKey = outbox.partitionInfos().associateBy { it.key }
    return DomainOutboxPartition.all.map { partition ->
      val info = partitionInfosByKey[partition.key]
      OutboxPartitionStats(
        name = partition.key,
        status = info?.status?.name ?: OutboxPartitionStatus.ACTIVE.name,
        documentCount = info?.eventCount ?: 0L,
        blockedUntil = info?.pausedUntil?.toKotlinInstant(),
        eventTypeCounts = info?.eventPerTypeCount
          ?.entries
          ?.map { (eventType, count) -> OutboxEventTypeCount(eventType = eventType, count = count) }
          ?.sortedByDescending { it.count }
          ?: emptyList(),
      )
    }
  }

  override fun getTasksByPartition(partitionKey: String): List<OutboxTask> {
    val partition = DomainOutboxPartition.all.first { it.key == partitionKey }
    return outbox.eventsForPartition(partition).map { task ->
      OutboxTask(
        eventType = task.eventType,
        deduplicationKey = task.deduplicationKey,
        priority = task.priority.name,
        status = task.status.name,
        attempts = task.attempts,
        nextRetryAt = task.nextRetryAt?.toKotlinInstant(),
        createdAt = task.createdAt.toKotlinInstant(),
        lastError = task.lastError,
      )
    }
  }

  companion object : KLogging()
}
