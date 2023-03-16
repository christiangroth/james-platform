package de.chrgroth.james.workspace

import arrow.core.ValidatedNel
import java.util.UUID
import de.chrgroth.james.Error

interface WorkspaceQueryPersistencePort {
    fun getOrError(workspaceId: UUID): ValidatedNel<Error, Workspace>

    // TODO #16 what about paging and how to design filter parameters??
    fun findForUser(userId: UUID): ValidatedNel<Error, List<Workspace>>
}

interface WorkspaceCommandPersistencePort {
    fun upsert(workspace: Workspace): ValidatedNel<Error, Workspace>
    fun delete(workspaceId: UUID): ValidatedNel<Error, Unit>
}
