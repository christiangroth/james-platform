package de.chrgroth.james.workspace

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import de.chrgroth.james.app.AppQueryPersistencePort
import de.chrgroth.james.fold
import de.chrgroth.james.foldAndShrink
import java.util.UUID

// TODO #28 explicit return type notations (EVERYWHERE!) / force it?

// TODO #22 need to check if user is active

interface WorkspaceUseCases {
    fun createWorkspace(userId: UUID, name: String): Maybe<Workspace>
    fun reorderWorkspaces(userId: UUID, order: List<UUID>): Maybe<Unit>
    fun renameWorkspace(id: UUID, newName: String): Maybe<Workspace>
    fun deleteWorkspace(id: UUID): Maybe<Unit>
}

interface AppInstallationUseCases {
    fun installApp(workspaceId: UUID, appId: UUID, appVersion: Semver): Maybe<AppInstallation>
    fun nameApp(workspaceId: UUID, appInstallationId: UUID, nameSupplement: String?): Maybe<AppInstallation>
    fun updateApp(workspaceId: UUID, appInstallationId: UUID, targetVersion: Semver): Maybe<AppInstallation>
    fun reorderApps(workspaceId: UUID, order: List<UUID>): Maybe<Workspace>
    fun moveApp(sourceWorkspaceId: UUID, appInstallationId: UUID, targetWorkspaceId: UUID): Maybe<Pair<Workspace, Workspace>>
    fun uninstallApp(workspaceId: UUID, appInstallationId: UUID): Maybe<Workspace>
}

internal class WorkspaceUseCasesService(
    private val queryPersistence: WorkspaceQueryPersistencePort,
    private val commandPersistence: WorkspaceCommandPersistencePort,
) : WorkspaceUseCases {

    override fun createWorkspace(userId: UUID, name: String): Maybe<Workspace> =
        queryPersistence.findForUser(userId).flatMap { persistentWorkspaces ->
            val currentMaxOrder = persistentWorkspaces.maxOfOrNull { it.order } ?: -1
            Workspace.create(userId = userId, order = currentMaxOrder + 1, name = name)
        }.flatMap {
            commandPersistence.upsert(it)
        }

    override fun reorderWorkspaces(userId: UUID, order: List<UUID>): Maybe<Unit> =
        queryPersistence.findForUser(userId).flatMap { persistentWorkspaces ->
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
                        .flatMap { updatedWorkspace -> commandPersistence.upsert(updatedWorkspace) }
                }
                    .filterIsInstance<Error<Workspace>>()
                    .map { it as Error<Unit> }
                    .fold() ?: Result(Unit)
        }

    override fun renameWorkspace(id: UUID, newName: String): Maybe<Workspace> =
        queryPersistence.getOrError(id).flatMap {
            it.changeName(newName)
        }.flatMap {
            commandPersistence.upsert(it)
        }

    override fun deleteWorkspace(id: UUID): Maybe<Unit> =
        queryPersistence.getOrError(id).flatMap { workspace ->
            workspace.verifyDeletion()
        }.flatMap {
            commandPersistence.delete(id)
        }
}

internal class AppInstallationUseCasesService(
    // TODO #32 remove dependency
    private val appQueryPersistence: AppQueryPersistencePort,
    private val queryPersistence: WorkspaceQueryPersistencePort,
    private val commandPersistence: WorkspaceCommandPersistencePort,
) : AppInstallationUseCases {

    override fun installApp(workspaceId: UUID, appId: UUID, appVersion: Semver): Maybe<AppInstallation> =
        appQueryPersistence.getOrError(appId, appVersion).flatMap {
            queryPersistence.getOrError(workspaceId)
        }.flatMap {
            it.installApp(appId, appVersion)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.appInstallations.first { appInstallation ->
                appInstallation.appId == appId && appInstallation.version == appVersion
            }
        }

    override fun nameApp(workspaceId: UUID, appInstallationId: UUID, nameSupplement: String?) =
        queryPersistence.getOrError(workspaceId).flatMap {
            it.nameAppInstallation(appInstallationId, nameSupplement)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.appInstallations.first { appInstallation ->
                appInstallation.id == appInstallationId
            }
        }

    // TODO #29 allow multiple errors
    override fun updateApp(workspaceId: UUID, appInstallationId: UUID, targetVersion: Semver): Maybe<AppInstallation> =
        queryPersistence.getOrError(workspaceId).flatMap { workspace ->
            workspace.getAppInstallationOrError(appInstallationId).flatMap { appInstallation ->
                appQueryPersistence.getOrError(appInstallation.appId, targetVersion).flatMap { _ ->
                    workspace.updateAppInstallation(appInstallationId, targetVersion)
                }
            }
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.appInstallations.first { appInstallation ->
                appInstallation.id == appInstallationId
            }
        }

    override fun reorderApps(workspaceId: UUID, order: List<UUID>): Maybe<Workspace> =
        queryPersistence.getOrError(workspaceId).flatMap {
            it.reorderAppInstallations(order)
        }.flatMap { updatedWorkspace ->
            commandPersistence.upsert(updatedWorkspace)
        }

    // TODO #29 allow multiple errors
    // TODO #5 trigger data movement, if needed?
    override fun moveApp(sourceWorkspaceId: UUID, appInstallationId: UUID, targetWorkspaceId: UUID): Maybe<Pair<Workspace, Workspace>> =
        queryPersistence.getOrError(sourceWorkspaceId).flatMap { source ->
            source.getAppInstallationOrError(appInstallationId).flatMap { appInstallation ->
                queryPersistence.getOrError(targetWorkspaceId).flatMap { target ->
                    persistAppMovement(source, appInstallation, target)
                }
            }
        }

    private fun persistAppMovement(source: Workspace, appInstallation: AppInstallation, target: Workspace) =
        target.acceptAppMigration(appInstallation).flatMap { updatedTarget ->
            source.acceptAppDemigration(appInstallation).flatMap { updatedSource ->
                commandPersistence.upsert(updatedTarget).flatMap { persistedTarget ->
                    commandPersistence.upsert(updatedSource).flatMap { persistedSource ->
                        Result(persistedSource to persistedTarget)
                    }
                }
            }
        }

    override fun uninstallApp(workspaceId: UUID, appInstallationId: UUID): Maybe<Workspace> =
        queryPersistence.getOrError(workspaceId).flatMap {
            it.uninstallApp(appInstallationId)
        }.flatMap {
            commandPersistence.upsert(it)
        }
}
