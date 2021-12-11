package de.chrgroth.james.workspace

import de.chrgroth.james.Maybe
import java.util.UUID

interface WorkspaceQueryPort {
    fun getWorkspace(workspaceId: UUID): Maybe<Workspace?>
    fun getWorkspacesForUser(userId: UUID): Maybe<List<Workspace>>
}

internal class WorkspaceQueryAdapter(private val queryPersistence: WorkspaceQueryPersistencePort) : WorkspaceQueryPort {

    override fun getWorkspace(workspaceId: UUID): Maybe<Workspace?> {
        return queryPersistence.get(workspaceId)
    }

    override fun getWorkspacesForUser(userId: UUID): Maybe<List<Workspace>> {
        return queryPersistence.findForUser(userId)
    }
}
