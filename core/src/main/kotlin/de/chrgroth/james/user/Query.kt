package de.chrgroth.james.user

import de.chrgroth.james.Maybe
import java.util.UUID

interface UserQueryPort {
    fun getUsers(): Maybe<Set<User>>
    fun getUser(id: UUID): Maybe<User?>
    fun getUserByEmail(email: String): Maybe<User?>
    fun findUsersByNameInfix(nameInfix: String): Maybe<Set<User>>
}

interface UserWorkspaceQueryPort {
    fun getWorkspaces(userId: UUID): Maybe<Set<UserWorkspace>>
    fun getWorkspace(userId: UUID, workspaceId: UUID): Maybe<UserWorkspace?>
    fun findWorkspacesByNameInfix(userId: UUID, nameInfix: String): Maybe<Set<UserWorkspace>>
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

internal class UserWorkspaceQueryAdapter(private val queryPersistence: UserWorkspaceQueryPersistencePort) : UserWorkspaceQueryPort {

    override fun getWorkspaces(userId: UUID): Maybe<Set<UserWorkspace>> {
        return queryPersistence.find(userId)
    }

    override fun getWorkspace(userId: UUID, workspaceId: UUID): Maybe<UserWorkspace?> {
        return queryPersistence.get(userId, workspaceId)
    }

    // TODO #16 move filtering to persistence
    override fun findWorkspacesByNameInfix(userId: UUID, nameInfix: String): Maybe<Set<UserWorkspace>> {
        return queryPersistence.find(userId).map { result ->
            result.filter { it.name.matches(Regex(".*${nameInfix}.*")) }.toSet()
        }
    }
}
