package de.chrgroth.james.workspace

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import de.chrgroth.james.app.AppQueryPersistencePort
import de.chrgroth.james.fold
import java.util.UUID

// TODO #28 explicit return type notations (EVERYWHERE!) / force it?
// TODO #25 saving wrong instance in persistenceCallback?

// TODO #22 need to check if user is active

interface WorkspaceCommandPort {
    fun createWorkspace(userId: UUID, name: String): Maybe<Workspace>
    fun reorderWorkspaces(userId: UUID, order: List<UUID>): Maybe<Unit>
    fun renameWorkspace(id: UUID, newName: String): Maybe<Workspace>
    fun deleteWorkspace(id: UUID): Maybe<Unit>

    fun installApp(id: UUID, appId: UUID, appVersion: Semver): Maybe<AppInstallation>
    fun nameApp(id: UUID, appInstallationId: UUID, nameSupplement: String?): Maybe<AppInstallation>
    fun updateApp(id: UUID, appInstallationId: UUID, targetVersion: Semver): Maybe<AppInstallation>
    fun moveApp(sourceWorkspaceId: UUID, appInstallationId: UUID, targetWorkspaceId: UUID): Maybe<Pair<Workspace, Workspace>>
    fun uninstallApp(id: UUID, appInstallationId: UUID): Maybe<Workspace>
}

internal class WorkspaceCommandAdapter(
    private val appQueryPersistence: AppQueryPersistencePort,
    private val queryPersistence: WorkspaceQueryPersistencePort,
    private val commandPersistence: WorkspaceCommandPersistencePort,
) : WorkspaceCommandPort {

    override fun createWorkspace(userId: UUID, name: String): Maybe<Workspace> {
        val currentMaxOrder = queryPersistence.findForUser(userId).map { persistentWorkspaces ->
            persistentWorkspaces.maxOfOrNull { it.order } ?: -1
        }
        val newOrder = when (currentMaxOrder) {
            is Result -> currentMaxOrder.value + 1
            else -> 0
        }
        return Workspace.create(userId, newOrder, name).flatMap {
            commandPersistence.upsert(it)
        }
    }

    override fun reorderWorkspaces(userId: UUID, order: List<UUID>): Maybe<Unit> =
        queryPersistence.findForUser(userId).flatMap { persistentWorkspaces ->
            val existingIds = persistentWorkspaces.map { it.id }
            val newIds = order.minus(existingIds.toSet())
            val missingIds = existingIds.minus(order.toSet())
            if (newIds.isNotEmpty()) {
                Error(
                    code = WorkspaceErrorCodes.REORDER_WORKSPACES_UNKNOWN_IDS,
                    details = newIds.toString(),
                )
            } else if (missingIds.isNotEmpty()) {
                Error(
                    code = WorkspaceErrorCodes.REORDER_WORKSPACES_MISSING_IDS,
                    details = missingIds.toString(),
                )
            } else {
                val reorderingResults = order.mapIndexed { index, workspaceId ->
                    persistentWorkspaces.first { it.id == workspaceId }.changeOrder(index.toLong())
                        .flatMap { updatedWorkspace -> commandPersistence.upsert(updatedWorkspace) }
                }

                @Suppress("UNCHECKED_CAST")
                reorderingResults.filterIsInstance<Error<Workspace>>()
                    .map { it as Error<Unit> }
                    .fold() ?: Result(Unit)
            }
        }

    override fun renameWorkspace(id: UUID, newName: String): Maybe<Workspace> =
        id.loadWorkspaceAndInvoke({ it.changeName(newName) }) { workspace, _ ->
            commandPersistence.upsert(workspace)
        }

    override fun deleteWorkspace(id: UUID): Maybe<Unit> =
        id.loadWorkspaceAndInvoke({ it.verifyDeletion() }) { _, _ ->
            commandPersistence.delete(id)
        }

    override fun installApp(id: UUID, appId: UUID, appVersion: Semver): Maybe<AppInstallation> =
        appQueryPersistence.getOrError(appId, appVersion).flatMap { _ ->
            id.loadWorkspaceAndInvoke({ it.installApp(appId, appVersion) }) { workspace, _ ->
                commandPersistence.upsert(workspace).map {
                    it.appInstallations.first { appInstallation ->
                        appInstallation.appId == appId && appInstallation.version == appVersion
                    }
                }
            }
        }

    override fun nameApp(id: UUID, appInstallationId: UUID, nameSupplement: String?) =
        id.loadWorkspaceAndInvoke({ it.nameAppInstallation(appInstallationId, nameSupplement) }) { workspace, _ ->
            commandPersistence.upsert(workspace).map {
                it.appInstallations.first { appInstallation ->
                    appInstallation.id == appInstallationId
                }
            }
        }

    override fun updateApp(id: UUID, appInstallationId: UUID, targetVersion: Semver): Maybe<AppInstallation> =
        id.loadWorkspaceAndInvoke({ workspace ->
            workspace.modifyAppInstallation(appInstallationId) { appInstallation ->
                appQueryPersistence.getOrError(appInstallation.appId, targetVersion).flatMap { _ ->
                    appInstallation.changeVersion(targetVersion)
                }
            }
        }) { workspace, _ ->
            commandPersistence.upsert(workspace).map {
                it.appInstallations.first { appInstallation ->
                    appInstallation.id == appInstallationId
                }
            }
        }

    // TODO #5 trigger data movement, if needed?
    override fun moveApp(sourceWorkspaceId: UUID, appInstallationId: UUID, targetWorkspaceId: UUID): Maybe<Pair<Workspace, Workspace>> =
        queryPersistence.getOrError(sourceWorkspaceId).flatMap { source ->
            source.getAppOrError(appInstallationId).flatMap { appInstallation ->
                queryPersistence.getOrError(targetWorkspaceId).flatMap { target ->
                    persistAppMovement(source, appInstallation, target)
                }
            }
        }

    private fun persistAppMovement(source: Workspace, appInstallation: AppInstallation, target: Workspace) =
        target.acceptAppMigration(appInstallation).flatMap { updatedTarget ->
            commandPersistence.upsert(updatedTarget).flatMap { persistedTarget ->
                source.uninstallApp(appInstallation.id).flatMap { updatedSource ->
                    commandPersistence.upsert(updatedSource).flatMap { persistedSource ->
                        Result(persistedSource to persistedTarget)
                    }
                }
            }
        }

    override fun uninstallApp(id: UUID, appInstallationId: UUID): Maybe<Workspace> =
        id.loadWorkspaceAndInvoke({ it.uninstallApp(appInstallationId) }) { workspace, _ ->
            commandPersistence.upsert(workspace)
        }.map { it }

    private fun <R, S> UUID.loadWorkspaceAndInvoke(
        workspaceOperation: (Workspace) -> Maybe<R>,
        persistenceOperation: (Workspace, R) -> Maybe<S>,
    ) =
        queryPersistence.get(this).flatMap { workspace ->
            if (workspace == null) {
                Error(
                    code = WorkspaceErrorCodes.NOT_FOUND,
                    details = null,
                )
            } else {
                workspaceOperation(workspace).flatMap { persistenceOperation(workspace, it) }
            }
        }
}
