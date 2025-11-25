package de.chrgroth.james.workspace

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.DomainEvent
import de.chrgroth.james.EventBus
import java.util.UUID

// TODO #18 bulk insert on application startup
class ActiveAppVersionsCache(private val eventBus: EventBus) {
  private val data = mutableSetOf<Pair<UUID, Semver>>()

  init {
    eventBus.receiver<DomainEvent.AppVersionReleased> {
      add(it.appId, it.version)
    }
  }

  private fun add(id: UUID, version: Semver) {
    data.add(Pair(id, version))
  }

  fun contains(id: UUID, version: Semver) = data.contains(Pair(id, version))
}
