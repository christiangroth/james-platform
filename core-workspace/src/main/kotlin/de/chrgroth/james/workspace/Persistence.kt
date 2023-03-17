package de.chrgroth.james.workspace

import arrow.core.ValidatedNel
import java.util.UUID
import de.chrgroth.james.DomainError

interface WorkspaceQueryPersistencePort {
    fun getOrError(workspaceId: UUID): ValidatedNel<DomainError, Workspace>

    // TODO #16 what about paging and how to design filter parameters??
    fun findForUser(userId: UUID): ValidatedNel<DomainError, List<Workspace>>
}

interface WorkspaceCommandPersistencePort {
    fun upsert(workspace: Workspace): ValidatedNel<DomainError, Workspace>
    fun delete(workspaceId: UUID): ValidatedNel<DomainError, Unit>
}
