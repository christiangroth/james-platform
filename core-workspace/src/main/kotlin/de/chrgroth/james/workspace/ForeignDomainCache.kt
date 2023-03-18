package de.chrgroth.james.workspace

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.DomainEvent
import de.chrgroth.james.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import java.util.UUID

// TODO #29 bulk insert on application startup
object ActiveAppVersionsCache {
    private val data = mutableSetOf<Pair<UUID, Semver>>()

    // TODO #29 review / possible duplicate
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            EventBus.events.filterIsInstance<DomainEvent.AppVersionReleased>().collectLatest { add(it.appId, it.version) }
            EventBus.events.filterIsInstance<DomainEvent.AppVersionDeleted>().collectLatest { evict(it.appId, it.version) }
        }
    }

    private fun add(id: UUID, version: Semver) {
        data.add(Pair(id, version))
    }

    private fun evict(id: UUID, version: Semver) {
        data.remove(Pair(id, version))
    }

    fun contains(id: UUID, version: Semver) = data.contains(Pair(id, version))
}
