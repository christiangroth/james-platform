package de.chrgroth.james.app

import de.chrgroth.james.Maybe
import java.util.UUID

interface AppQueryPersistencePort {
    fun get(id: UUID): Maybe<App?>

    // TODO #16 what about paging and how to design filter parameters??
    fun find(): Maybe<Set<App>>
}

interface AppCommandPersistencePort {
    fun upsert(item: App): Maybe<App>
    fun delete(id: UUID): Maybe<Unit>
}
