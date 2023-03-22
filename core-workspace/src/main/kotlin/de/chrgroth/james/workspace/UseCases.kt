package de.chrgroth.james.workspace

import arrow.core.Validated
import arrow.core.ValidatedNel
import arrow.core.andThen
import com.github.glwithu06.semver.Semver
import de.chrgroth.james.DomainError
import de.chrgroth.james.createValidation
import de.chrgroth.james.reduceWithFirstValue
import java.util.UUID

// TODO #6 need to check if user is active

interface WorkspaceUseCases {
    fun createWorkspace(userId: UUID, name: String): ValidatedNel<DomainError, Workspace>
    fun reorderWorkspaces(userId: UUID, order: List<UUID>): ValidatedNel<DomainError, Unit>
    fun renameWorkspace(id: UUID, newName: String): ValidatedNel<DomainError, Workspace>
    fun deleteWorkspace(id: UUID): ValidatedNel<DomainError, Unit>
}

interface AppInstallationUseCases {
    fun installApp(workspaceId: UUID, appId: UUID, appVersion: Semver): ValidatedNel<DomainError, AppInstallation>
    fun nameApp(workspaceId: UUID, appInstallationId: UUID, nameSupplement: String?): ValidatedNel<DomainError, AppInstallation>
    fun updateApp(workspaceId: UUID, appInstallationId: UUID, targetVersion: Semver): ValidatedNel<DomainError, AppInstallation>
    fun reorderApps(workspaceId: UUID, order: List<UUID>): ValidatedNel<DomainError, Workspace>
    fun moveApp(sourceWorkspaceId: UUID, appInstallationId: UUID, targetWorkspaceId: UUID): ValidatedNel<DomainError, Pair<Workspace, Workspace>>
    fun uninstallApp(workspaceId: UUID, appInstallationId: UUID): ValidatedNel<DomainError, Workspace>
}

internal class WorkspaceUseCasesService(
    private val queryPersistence: WorkspaceQueryPersistencePort,
    private val commandPersistence: WorkspaceCommandPersistencePort,
) : WorkspaceUseCases {

    override fun createWorkspace(userId: UUID, name: String): ValidatedNel<DomainError, Workspace> =
        queryPersistence.findForUser(userId).andThen { persistentWorkspaces ->
            val currentMaxOrder = persistentWorkspaces.maxOfOrNull { it.order } ?: -1
            Workspace.create(userId = userId, order = currentMaxOrder + 1, name = name)
        }.andThen {
            commandPersistence.upsert(it)
        }

    override fun reorderWorkspaces(userId: UUID, order: List<UUID>): ValidatedNel<DomainError, Unit> =
        queryPersistence.findForUser(userId).andThen { persistentWorkspaces ->
            val existingIds = persistentWorkspaces.map { it.id }

            val unknownIdsValidation: ValidatedNel<DomainError, Unit> = order.minus(existingIds.toSet()).let {
                createValidation(
                    errorCondition = it.isNotEmpty(),
                    domainErrorCode = WorkspaceDomainErrorCodes.REORDER_WORKSPACES_UNKNOWN_IDS,
                    errorDetails = it.toString(),
                ) {}
            }

            val missingIdsValidation: ValidatedNel<DomainError, Unit> = existingIds.minus(order.toSet()).let {
                createValidation(
                    errorCondition = it.isNotEmpty(),
                    domainErrorCode = WorkspaceDomainErrorCodes.REORDER_WORKSPACES_MISSING_IDS,
                    errorDetails = it.toString(),
                ) {}
            }

            listOf(unknownIdsValidation, missingIdsValidation).reduceWithFirstValue().andThen {
                order.mapIndexed { index, workspaceId ->
                    persistentWorkspaces.first { it.id == workspaceId }.changeOrder(index.toLong())
                        .andThen { updatedWorkspace -> commandPersistence.upsert(updatedWorkspace) }
                }.reduceWithFirstValue().map { }
            }
        }

    override fun renameWorkspace(id: UUID, newName: String): ValidatedNel<DomainError, Workspace> =
        queryPersistence.getOrError(id).andThen {
            it.changeName(newName)
        }.andThen {
            commandPersistence.upsert(it)
        }

    override fun deleteWorkspace(id: UUID): ValidatedNel<DomainError, Unit> =
        queryPersistence.getOrError(id).andThen { workspace ->
            workspace.verifyDeletion()
        }.andThen {
            commandPersistence.delete(id)
        }
}

internal class AppInstallationUseCasesService(
    private val queryPersistence: WorkspaceQueryPersistencePort,
    private val commandPersistence: WorkspaceCommandPersistencePort,
    private val activeAppVersionsCache: ActiveAppVersionsCache,
) : AppInstallationUseCases {

    override fun installApp(workspaceId: UUID, appId: UUID, appVersion: Semver): ValidatedNel<DomainError, AppInstallation> =
        createValidation(
            errorCondition = !activeAppVersionsCache.contains(appId, appVersion),
            domainErrorCode = WorkspaceDomainErrorCodes.APP_VERSION_UNKNOWN,
            errorDetails = null,
        ) {}.andThen {
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

    override fun updateApp(workspaceId: UUID, appInstallationId: UUID, targetVersion: Semver): ValidatedNel<DomainError, AppInstallation> =
        queryPersistence.getOrError(workspaceId).andThen { workspace ->
            workspace.getAppInstallationOrError(appInstallationId).andThen { appInstallation ->
                createValidation(
                    errorCondition = !activeAppVersionsCache.contains(appInstallation.appId, targetVersion),
                    domainErrorCode = WorkspaceDomainErrorCodes.APP_VERSION_UNKNOWN,
                    errorDetails = null,
                ) {}.andThen { _ ->
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

    override fun reorderApps(workspaceId: UUID, order: List<UUID>): ValidatedNel<DomainError, Workspace> =
        queryPersistence.getOrError(workspaceId).andThen {
            it.reorderAppInstallations(order)
        }.andThen { updatedWorkspace ->
            commandPersistence.upsert(updatedWorkspace)
        }

    // TODO #2 trigger data movement, if needed?
    override fun moveApp(sourceWorkspaceId: UUID, appInstallationId: UUID, targetWorkspaceId: UUID): ValidatedNel<DomainError, Pair<Workspace, Workspace>> =
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
                        Validated.validNel(persistedSource to persistedTarget)
                    }
                }
            }
        }

    override fun uninstallApp(workspaceId: UUID, appInstallationId: UUID): ValidatedNel<DomainError, Workspace> =
        queryPersistence.getOrError(workspaceId).andThen {
            it.uninstallApp(appInstallationId)
        }.andThen {
            commandPersistence.upsert(it)
        }
}
