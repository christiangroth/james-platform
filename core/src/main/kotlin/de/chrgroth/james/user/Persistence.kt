package de.chrgroth.james.user

import de.chrgroth.james.Maybe
import java.util.UUID

interface UserQueryPersistencePort {
    fun getOrError(id: UUID): Maybe<User>
    fun getByEmail(email: String): Maybe<User?>

    // TODO #16 what about paging and how to design filter parameters??
    fun find(): Maybe<Set<User>>
}

interface UserCommandPersistencePort {
    fun upsert(item: User): Maybe<User>
}
