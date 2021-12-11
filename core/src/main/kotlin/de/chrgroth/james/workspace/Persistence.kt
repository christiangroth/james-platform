package de.chrgroth.james.workspace

import de.chrgroth.james.Maybe
import java.util.UUID

interface WorkspaceQueryPersistencePort {
    fun get(workspaceId: UUID): Maybe<Workspace?>
    fun getOrError(workspaceId: UUID): Maybe<Workspace>

    // TODO #16 what about paging and how to design filter parameters??
    fun findForUser(userId: UUID): Maybe<List<Workspace>>
}

interface WorkspaceCommandPersistencePort {
    fun upsert(workspace: Workspace): Maybe<Workspace>
    fun delete(workspaceId: UUID): Maybe<Unit>
}
