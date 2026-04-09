package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@ApplicationScoped
class DashboardSseAdapter : DashboardRefreshPort {

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

  override fun notifyUserPlaybackData(userId: UserId) = emitToUser(userId.value, "refresh-playback-data")

  override fun notifyUserPlaylistMetadata(userId: UserId) = emitToUser(userId.value, "refresh-playlist-metadata")

  override fun notifyUserPlaylistChecks(userId: UserId) = emitToUser(userId.value, "refresh-playlist-checks")

  override fun notifyCatalogData() = notifyAllUsers("refresh-catalog-data")

  private fun notifyAllUsers(event: String) = emittersByUser.keys.toList().forEach { emitToUser(it, event) }

  private fun emitToUser(userId: String, event: String) {
    emittersByUser[userId]?.forEach { runCatching { it.emit(event) } }
  }
}
