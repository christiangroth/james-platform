package de.chrgroth.james.user

import arrow.core.ValidatedNel
import de.chrgroth.james.Error
import java.util.UUID

interface UserQueryPersistencePort {
    fun getOrError(id: UUID): ValidatedNel<Error, User>
    fun getByEmail(email: String): ValidatedNel<Error, User?>

    // TODO #16 what about paging and how to design filter parameters??
    fun find(): ValidatedNel<Error, Set<User>>
}

interface UserCommandPersistencePort {
    fun upsert(item: User): ValidatedNel<Error, User>
}
