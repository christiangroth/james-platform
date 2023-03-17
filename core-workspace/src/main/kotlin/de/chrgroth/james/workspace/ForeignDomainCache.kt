package de.chrgroth.james.workspace

import com.github.glwithu06.semver.Semver
import java.util.UUID

// TODO #29 update via events on app version release (and delete?)
class AppVersionCache {
    private val data = mutableSetOf<Pair<UUID, Semver>>()

    fun add(id: UUID, version: Semver) {
        data.add(Pair(id, version))
    }

    fun contains(id: UUID, version: Semver) = data.contains(Pair(id, version))

    fun evict(id: UUID, version: Semver) {
        data.remove(Pair(id, version))
    }
}
