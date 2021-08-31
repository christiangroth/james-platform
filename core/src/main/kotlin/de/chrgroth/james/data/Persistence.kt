package de.chrgroth.james.data

import de.chrgroth.james.Maybe
import de.chrgroth.james.app.App
import de.chrgroth.james.user.User
import de.chrgroth.james.user.UserWorkspace
import java.util.UUID

// TODO #3 split to user and workspace persistence

interface UserQueryPersistencePort {
    fun get(id: UUID): Maybe<User?>
    fun getByEmail(email: String): Maybe<User?>

    // TODO #16 what about paging and how to design filter parameters??
    fun find(): Maybe<Set<User>>

    fun getWorkspaces(userId: UUID): Maybe<Set<UserWorkspace>>
    fun getWorkspace(userId: UUID, workspaceId: UUID): Maybe<UserWorkspace?>

    // TODO #16 what about paging and how to design filter parameters??
    fun findWorkspaces(userId: UUID): Maybe<Set<UserWorkspace>>
}

// TODO #3 split to user and workspace persistence

interface UserCommandPersistencePort {
    fun upsert(item: User): Maybe<User>
    fun delete(id: UUID): Maybe<Unit>
}
