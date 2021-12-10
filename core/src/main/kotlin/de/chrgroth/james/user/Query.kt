package de.chrgroth.james.user

import de.chrgroth.james.Maybe
import java.util.UUID

interface UserQueryPort {
    fun getUsers(): Maybe<Set<User>>
    fun getUser(id: UUID): Maybe<User?>
    fun getUserByEmail(email: String): Maybe<User?>
    fun findUsersByNameInfix(nameInfix: String): Maybe<Set<User>>
}

internal class UserQueryAdapter(private val queryPersistence: UserQueryPersistencePort) : UserQueryPort {
    override fun getUsers(): Maybe<Set<User>> {
        return queryPersistence.find()
    }

    override fun getUser(id: UUID): Maybe<User?> {
        return queryPersistence.get(id)
    }

    override fun getUserByEmail(email: String): Maybe<User?> {
        return queryPersistence.getByEmail(email)
    }

    // TODO #16 move filtering to persistence
    override fun findUsersByNameInfix(nameInfix: String): Maybe<Set<User>> {
        return queryPersistence.find().map { result ->
            result.filter { it.name.matches(Regex(".*${nameInfix}.*")) }.toSet()
        }
    }
}
