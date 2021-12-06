package de.chrgroth.james.user

import de.chrgroth.james.Maybe
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
    fun upsert(userId: UUID, workspace: UserWorkspace): Maybe<UserWorkspace>
    fun delete(userId: UUID, worksoaceId: UUID): Maybe<Unit>
}
