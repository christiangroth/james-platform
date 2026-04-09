package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPartitionObserver
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxTaskCountObserver
import de.chrgroth.spotify.control.domain.port.out.infra.OutgoingRequestStatsObserver
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackDetectedObserver
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@ApplicationScoped
class HealthSseAdapter : OutboxPartitionObserver, OutgoingRequestStatsObserver, OutboxTaskCountObserver, PlaybackDetectedObserver {

  private val emittersByUser = ConcurrentHashMap<String, CopyOnWriteArrayList<MultiEmitter<in String>>>()

  fun stream(userId: UserId): Multi<String> = Multi.createFrom().emitter { emitter ->
    emittersByUser.getOrPut(userId.value) { CopyOnWriteArrayList() }.add(emitter)
    emitter.onTermination {
      emittersByUser.computeIfPresent(userId.value) { _, list ->
        list.remove(emitter)
        list.takeIf { it.isNotEmpty() }
      }
    }
  }

  @Suppress("UnusedParameter")
  override fun onPartitionPaused(partitionKey: String, reason: String) = notifyAllUsers("refresh-outbox-partitions")

  @Suppress("UnusedParameter")
  override fun onPartitionActivated(partitionKey: String) = notifyAllUsers("refresh-outbox-partitions")

  override fun onRequestRecorded() = notifyAllUsers("refresh-outgoing-http-calls")

  override fun onOutboxTaskCountChanged() = notifyAllUsers("refresh-outbox-partitions")

  override fun onPlaybackDetected() = notifyAllUsers("refresh-playback-state")

  private fun notifyAllUsers(event: String) = emittersByUser.keys.toList().forEach { emitToUser(it, event) }

  private fun emitToUser(userId: String, event: String) {
    emittersByUser[userId]?.forEach { runCatching { it.emit(event) } }
  }
}
