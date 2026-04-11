package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.out.infra.DashboardRefreshPort
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@ApplicationScoped
class DashboardSseAdapter : DashboardRefreshPort {

  private val emittersByUser = ConcurrentHashMap<String, CopyOnWriteArrayList<MultiEmitter<in String>>>()

  fun stream(username: Username): Multi<String> = Multi.createFrom().emitter { emitter ->
    emittersByUser.getOrPut(username.value) { CopyOnWriteArrayList() }.add(emitter)
    emitter.onTermination {
      emittersByUser.computeIfPresent(username.value) { _, list ->
        list.remove(emitter)
        list.takeIf { it.isNotEmpty() }
      }
    }
  }

  override fun notifyUserPlaybackData(username: Username) = emitToUser(username.value, "refresh-playback-data")

  override fun notifyUserPlaylistMetadata(username: Username) = emitToUser(username.value, "refresh-playlist-metadata")

  override fun notifyUserPlaylistChecks(username: Username) = emitToUser(username.value, "refresh-playlist-checks")

  override fun notifyCatalogData() = notifyAllUsers("refresh-catalog-data")

  private fun notifyAllUsers(event: String) = emittersByUser.keys.toList().forEach { emitToUser(it, event) }

  private fun emitToUser(username: String, event: String) {
    emittersByUser[username]?.forEach { runCatching { it.emit(event) } }
  }
}
