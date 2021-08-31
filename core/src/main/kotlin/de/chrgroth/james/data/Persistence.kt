package de.chrgroth.james.data

import de.chrgroth.james.Maybe
import de.chrgroth.james.app.App
import de.chrgroth.james.user.User
import de.chrgroth.james.user.UserWorkspace
import java.util.UUID

interface UserQueryPersistencePort {
    fun get(id: UUID): Maybe<User?>
    fun getByEmail(email: String): Maybe<User?>

    // TODO #16 what about paging and how to design filter parameters??
    fun find(): Maybe<Set<User>>
}

interface UserWorkspaceQueryPersistencePort {
    fun get(userId: UUID, workspaceId: UUID): Maybe<UserWorkspace?>

    // TODO #16 what about paging and how to design filter parameters??
    fun find(userId: UUID): Maybe<Set<UserWorkspace>>
}

interface UserCommandPersistencePort {
    fun upsert(item: User): Maybe<User>
    fun delete(id: UUID): Maybe<Unit>
}

interface UserWorkspaceCommandPersistencePort {
    fun upsert(userId: UUID, item: User): Maybe<User>
    fun delete(userId: UUID, id: UUID): Maybe<Unit>
}
