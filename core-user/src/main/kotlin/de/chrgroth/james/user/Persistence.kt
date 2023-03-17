package de.chrgroth.james.user

import arrow.core.ValidatedNel
import de.chrgroth.james.DomainError
import java.util.UUID

interface UserQueryPersistencePort {
    fun getOrError(id: UUID): ValidatedNel<DomainError, User>
    fun getByEmail(email: String): ValidatedNel<DomainError, User?>

    // TODO #16 what about paging and how to design filter parameters??
    fun find(): ValidatedNel<DomainError, Set<User>>
}

interface UserCommandPersistencePort {
    fun upsert(item: User): ValidatedNel<DomainError, User>
}
