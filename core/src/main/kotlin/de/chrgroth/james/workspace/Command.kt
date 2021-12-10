package de.chrgroth.james.workspace

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import java.util.UUID

// TODO #25 explicit return type notations (EVERYWHERE!) / force it?
// TODO #25 saving wrong instance in persistencCallback?

// TODO #25 methods for sorting/ordering workspaces

// TODO #22 need to check if user is active

interface WorkspaceCommandPort {
    fun createWorkspace(userId: UUID, name: String): Maybe<Workspace>
    fun renameWorkspace(id: UUID, newName: String): Maybe<Workspace>
    fun deleteWorkspace(id: UUID): Maybe<Unit>

    fun installApp(id: UUID, appId: UUID, appVersion: Semver): Maybe<AppInstallation>
    fun nameApp(id: UUID, appInstallationId: UUID, nameSupplement: String?): Maybe<AppInstallation>
    fun updateApp(id: UUID, appInstallationId: UUID, targetVersion: Semver): Maybe<AppInstallation>
    fun moveApp(sourceWorkspaceId: UUID, appInstallationId: UUID, targetWorkspaceId: UUID): Maybe<Pair<Workspace, Workspace>>
    fun uninstallApp(id: UUID, appInstallationId: UUID): Maybe<Workspace>
}

internal class WorkspaceCommandAdapter(
    private val queryPersistence: WorkspaceQueryPersistencePort,
    private val commandPersistence: WorkspaceCommandPersistencePort,
) : WorkspaceCommandPort {

    override fun createWorkspace(userId: UUID, name: String): Maybe<Workspace> =
        Workspace.create(userId, name).flatMap {
            commandPersistence.upsert(it)
        }

    override fun renameWorkspace(id: UUID, newName: String): Maybe<Workspace> =
        id.loadWorkspaceAndInvoke({ it.rename(newName) }) { workspace, _ ->
            commandPersistence.upsert(workspace)
        }

    override fun deleteWorkspace(id: UUID): Maybe<Unit> =
        id.loadWorkspaceAndInvoke({ it.canBeDeleted() }) { _, _ ->
            commandPersistence.delete(id)
        }

    // TODO #25 check if version exists/is released
    override fun installApp(id: UUID, appId: UUID, appVersion: Semver): Maybe<AppInstallation> =
        id.loadWorkspaceAndInvoke({ it.installApp(appId, appVersion) }) { workspace, _ ->
            commandPersistence.upsert(workspace).map {
                it.appInstallations.first { appInstallation ->
                    appInstallation.appId == appId && appInstallation.version == appVersion
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

    // TODO #25 check if version exists/is released
    override fun updateApp(id: UUID, appInstallationId: UUID, targetVersion: Semver): Maybe<AppInstallation> =
        id.loadWorkspaceAndInvoke({ it.updateAppInstallation(appInstallationId, targetVersion) }) { workspace, _ ->
            commandPersistence.upsert(workspace).map {
                it.appInstallations.first { appInstallation ->
                    appInstallation.id == appInstallationId
                }
            }
        }

    /*override fun moveAppInstallation(userId: UUID, workspaceId: UUID, appInstallationId: UUID, newWorkspaceId: UUID) =
       userId.loadUserAndInvoke({ it.moveAppInstallation(workspaceId, appInstallationId, newWorkspaceId) }) { user, _ ->
           userCommandPersistence.upsert(user).map {
               it.workspaces.first { workspace ->
                   workspace.id == newWorkspaceId
               }
           }
       }*/

    /*
@Suppress("ReturnCount")
internal fun moveAppInstallation(workspaceId: UUID, appInstallationId: UUID, newWorkspaceId: UUID): Maybe<User> {
    val sourceWorkspace = workspaces.firstOrNull() { it.id == workspaceId }
        ?: return Error(
            code = WorkspaceErrorCodes.NOT_FOUND,
            details = workspaceId.toString(),
        )

    val appInstallation = sourceWorkspace.appInstallations.firstOrNull { it.id == appInstallationId }
        ?: return Error(
            code = AppInstallationErrorCodes.NOT_FOUND,
            details = appInstallationId.toString(),
        )

    val targetWorkspace = workspaces.firstOrNull { it.id == newWorkspaceId }
        ?: return Error(
            code = WorkspaceErrorCodes.NOT_FOUND,
            details = newWorkspaceId.toString(),
        )

    return Maybe.Result(
        copy(
            workspaces = workspaces.map {
                when (it.id) {
                    sourceWorkspace.id -> it.copy(
                        appInstallations = it.appInstallations.filterNot { sourceInstallation ->
                            sourceInstallation.id == appInstallationId
                        }
                    )
                    targetWorkspace.id -> it.copy(appInstallations = it.appInstallations.plus(appInstallation))
                    else -> it
                }
            }
        )
    )
}
*/

    // TODO #5 trigger data movement, if needed?
    // TODO #25 ugly code
    override fun moveApp(sourceWorkspaceId: UUID, appInstallationId: UUID, targetWorkspaceId: UUID): Maybe<Pair<Workspace, Workspace>> =
        queryPersistence.getOrError(sourceWorkspaceId).flatMap { source ->
            source.getAppOrError(appInstallationId).flatMap { app ->
                queryPersistence.getOrError(targetWorkspaceId).flatMap { target ->
                    target.accommodateApp(app).flatMap { updatedTarget ->
                        commandPersistence.upsert(updatedTarget).flatMap { persistedTarget ->
                            source.removeApp(app).flatMap { updatedSource ->
                                commandPersistence.upsert(updatedSource).flatMap { persistedSource ->
                                    Result(persistedSource to persistedTarget)
                                }
                            }
                        }
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
