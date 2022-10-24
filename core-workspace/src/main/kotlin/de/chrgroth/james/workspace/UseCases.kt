package de.chrgroth.james.workspace

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.app.AppQueryPersistencePort
import de.chrgroth.james.fold
import de.chrgroth.james.foldAndShrink
import java.util.UUID

// TODO #28 explicit return type notations (EVERYWHERE!) / force it?

// TODO #22 need to check if user is active

interface WorkspaceUseCases {
    fun createWorkspace(userId: UUID, name: String): ValidatedNel<Error, Workspace>
    fun reorderWorkspaces(userId: UUID, order: List<UUID>): ValidatedNel<Error, Unit>
    fun renameWorkspace(id: UUID, newName: String): ValidatedNel<Error, Workspace>
    fun deleteWorkspace(id: UUID): ValidatedNel<Error, Unit>
}

interface AppInstallationUseCases {
    fun installApp(workspaceId: UUID, appId: UUID, appVersion: Semver): ValidatedNel<Error, AppInstallation>
    fun nameApp(workspaceId: UUID, appInstallationId: UUID, nameSupplement: String?): ValidatedNel<Error, AppInstallation>
    fun updateApp(workspaceId: UUID, appInstallationId: UUID, targetVersion: Semver): ValidatedNel<Error, AppInstallation>
    fun reorderApps(workspaceId: UUID, order: List<UUID>): ValidatedNel<Error, Workspace>
    fun moveApp(sourceWorkspaceId: UUID, appInstallationId: UUID, targetWorkspaceId: UUID): ValidatedNel<Error, Pair<Workspace, Workspace>>
    fun uninstallApp(workspaceId: UUID, appInstallationId: UUID): ValidatedNel<Error, Workspace>
}

internal class WorkspaceUseCasesService(
    private val queryPersistence: WorkspaceQueryPersistencePort,
    private val commandPersistence: WorkspaceCommandPersistencePort,
) : WorkspaceUseCases {

    override fun createWorkspace(userId: UUID, name: String): ValidatedNel<Error, Workspace> =
        queryPersistence.findForUser(userId).andThen { persistentWorkspaces ->
            val currentMaxOrder = persistentWorkspaces.maxOfOrNull { it.order } ?: -1
            Workspace.create(userId = userId, order = currentMaxOrder + 1, name = name)
        }.andThen {
            commandPersistence.upsert(it)
        }

    override fun reorderWorkspaces(userId: UUID, order: List<UUID>): ValidatedNel<Error, Unit> =
        queryPersistence.findForUser(userId).andThen { persistentWorkspaces ->
            val existingIds = persistentWorkspaces.map { it.id }

            val unknownIdsError: Error<Unit>? = order.minus(existingIds.toSet()).let {
                if (it.isNotEmpty()) {
                    Error(
                        code = WorkspaceErrorCodes.REORDER_WORKSPACES_UNKNOWN_IDS,
                        details = it.toString(),
                    )
                } else null
            }

            val missingIdsError: Error<Unit>? = existingIds.minus(order.toSet()).let {
                if (it.isNotEmpty()) {
                    Error(
                        code = WorkspaceErrorCodes.REORDER_WORKSPACES_MISSING_IDS,
                        details = it.toString(),
                    )
                } else null
            }

            @Suppress("UNCHECKED_CAST")
            listOf(unknownIdsError, missingIdsError).foldAndShrink()
                ?: order.mapIndexed { index, workspaceId ->
                    persistentWorkspaces.first { it.id == workspaceId }.changeOrder(index.toLong())
                        .andThen { updatedWorkspace -> commandPersistence.upsert(updatedWorkspace) }
                }
                    .filterIsInstance<Error<Workspace>>()
                    .map { it as Error<Unit> }
                    .fold() ?: Result(Unit)
        }

    override fun renameWorkspace(id: UUID, newName: String): ValidatedNel<Error, Workspace> =
        queryPersistence.getOrError(id).andThen {
            it.changeName(newName)
        }.andThen {
            commandPersistence.upsert(it)
        }

    override fun deleteWorkspace(id: UUID): ValidatedNel<Error, Unit> =
        queryPersistence.getOrError(id).andThen { workspace ->
            workspace.verifyDeletion()
        }.andThen {
            commandPersistence.delete(id)
        }
}

internal class AppInstallationUseCasesService(
    // TODO #32 remove dependency
    private val appQueryPersistence: AppQueryPersistencePort,
    private val queryPersistence: WorkspaceQueryPersistencePort,
    private val commandPersistence: WorkspaceCommandPersistencePort,
) : AppInstallationUseCases {

    override fun installApp(workspaceId: UUID, appId: UUID, appVersion: Semver): ValidatedNel<Error, AppInstallation> =
        appQueryPersistence.getOrError(appId, appVersion).andThen {
            queryPersistence.getOrError(workspaceId)
        }.andThen {
            it.installApp(appId, appVersion)
        }.andThen {
            commandPersistence.upsert(it)
        }.map {
            it.appInstallations.first { appInstallation ->
                appInstallation.appId == appId && appInstallation.version == appVersion
            }
        }

    override fun nameApp(workspaceId: UUID, appInstallationId: UUID, nameSupplement: String?) =
        queryPersistence.getOrError(workspaceId).andThen {
            it.nameAppInstallation(appInstallationId, nameSupplement)
        }.andThen {
            commandPersistence.upsert(it)
        }.map {
            it.appInstallations.first { appInstallation ->
                appInstallation.id == appInstallationId
            }
        }

    // TODO #29 allow multiple errors
    override fun updateApp(workspaceId: UUID, appInstallationId: UUID, targetVersion: Semver): ValidatedNel<Error, AppInstallation> =
        queryPersistence.getOrError(workspaceId).andThen { workspace ->
            workspace.getAppInstallationOrError(appInstallationId).andThen { appInstallation ->
                appQueryPersistence.getOrError(appInstallation.appId, targetVersion).andThen { _ ->
                    workspace.updateAppInstallation(appInstallationId, targetVersion)
                }
            }
        }.andThen {
            commandPersistence.upsert(it)
        }.map {
            it.appInstallations.first { appInstallation ->
                appInstallation.id == appInstallationId
            }
        }

    override fun reorderApps(workspaceId: UUID, order: List<UUID>): ValidatedNel<Error, Workspace> =
        queryPersistence.getOrError(workspaceId).andThen {
            it.reorderAppInstallations(order)
        }.andThen { updatedWorkspace ->
            commandPersistence.upsert(updatedWorkspace)
        }

    // TODO #29 allow multiple errors
    // TODO #5 trigger data movement, if needed?
    override fun moveApp(sourceWorkspaceId: UUID, appInstallationId: UUID, targetWorkspaceId: UUID): ValidatedNel<Error, Pair<Workspace, Workspace>> =
        queryPersistence.getOrError(sourceWorkspaceId).andThen { source ->
            source.getAppInstallationOrError(appInstallationId).andThen { appInstallation ->
                queryPersistence.getOrError(targetWorkspaceId).andThen { target ->
                    persistAppMovement(source, appInstallation, target)
                }
            }
        }

    private fun persistAppMovement(source: Workspace, appInstallation: AppInstallation, target: Workspace) =
        target.acceptAppMigration(appInstallation).andThen { updatedTarget ->
            source.acceptAppDemigration(appInstallation).andThen { updatedSource ->
                commandPersistence.upsert(updatedTarget).andThen { persistedTarget ->
                    commandPersistence.upsert(updatedSource).andThen { persistedSource ->
                        Result(persistedSource to persistedTarget)
                    }
                }
            }
        }

    override fun uninstallApp(workspaceId: UUID, appInstallationId: UUID): ValidatedNel<Error, Workspace> =
        queryPersistence.getOrError(workspaceId).andThen {
            it.uninstallApp(appInstallationId)
        }.andThen {
            commandPersistence.upsert(it)
        }
}
