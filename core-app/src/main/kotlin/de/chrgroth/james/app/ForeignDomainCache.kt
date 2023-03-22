package de.chrgroth.james.app

import de.chrgroth.james.DomainEvent
import de.chrgroth.james.EventBus
import java.util.UUID

// TODO bulk insert on application startup
class ActiveUsersCache(private val eventBus: EventBus) {
    private val data = mutableSetOf<UUID>()

    init {
        eventBus.receiver<DomainEvent.UserRegistered> {
            add(it.id)
        }
    }

    private fun add(id: UUID) {
        data.add(id)
    }

    fun contains(id: UUID) = data.contains(id)
}
