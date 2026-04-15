package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.user.Username
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@ApplicationScoped
class HealthSseAdapter {

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

  private fun notifyAllUsers(event: String) = emittersByUser.keys.toList().forEach { emitToUser(it, event) }

  private fun emitToUser(username: String, event: String) {
    emittersByUser[username]?.forEach { runCatching { it.emit(event) } }
  }
}
