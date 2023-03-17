package de.chrgroth.james.app

import java.util.UUID

// TODO #29 update via events on user create and delete
class UserCache {
    private val data = mutableSetOf<UUID>()

    fun add(id: UUID) {
        data.add(id)
    }

    fun contains(id: UUID) = data.contains(id)

    fun evict(id: UUID) {
        data.remove(id)
    }
}
