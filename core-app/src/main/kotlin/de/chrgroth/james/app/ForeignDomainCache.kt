package de.chrgroth.james.app

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
object ActiveUsersCache {
    private val data = mutableSetOf<UUID>()

    // TODO #29 review / possible duplicate
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            EventBus.events.filterIsInstance<DomainEvent.UserRegistered>().collectLatest { add(it.id) }
            EventBus.events.filterIsInstance<DomainEvent.UserDeactivated>().collectLatest { evict(it.id) }
            EventBus.events.filterIsInstance<DomainEvent.UserActivated>().collectLatest { add(it.id) }
            EventBus.events.filterIsInstance<DomainEvent.UserDeleted>().collectLatest { evict(it.id) }
        }
    }

    private fun add(id: UUID) {
        data.add(id)
    }

    private fun evict(id: UUID) {
        data.remove(id)
    }

    fun contains(id: UUID) = data.contains(id)
}
