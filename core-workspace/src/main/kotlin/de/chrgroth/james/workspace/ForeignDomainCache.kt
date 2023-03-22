package de.chrgroth.james.workspace

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.DomainEvent
import de.chrgroth.james.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.UUID

// TODO bulk insert on application startup
class ActiveAppVersionsCache(private val eventBus: EventBus) {
    private val data = mutableSetOf<Pair<UUID, Semver>>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
