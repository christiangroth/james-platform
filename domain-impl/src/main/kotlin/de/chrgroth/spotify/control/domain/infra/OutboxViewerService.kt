package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.model.infra.OutboxViewerPartition
import de.chrgroth.spotify.control.domain.port.`in`.infra.OutboxViewerPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

@ApplicationScoped
@Suppress("Unused")
class OutboxViewerService(private val outbox: OutboxPort) : OutboxViewerPort {

  override fun getPartitions(): List<OutboxViewerPartition> = runBlocking {
    DomainOutboxPartition.all
      .map { partition -> partition to async(Dispatchers.IO) { outbox.getTasksByPartition(partition.key) } }
      .map { (partition, deferred) -> OutboxViewerPartition(key = partition.key, tasks = deferred.await()) }
  }
}
