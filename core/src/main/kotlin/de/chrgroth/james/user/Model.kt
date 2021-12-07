package de.chrgroth.james.user

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import java.util.UUID

// TODO #25 split user and workspace/installed apps domains
// TODO #25 refactor usage of canBeDeleted to be private

private val simpleEmailPattern = Regex(".+@.+\\..+")

// TODO #25 ensure values are trimmed (or enforce usage of create function)
data class User(
    val id: UUID,
    val email: String,
    val name: String,
    val workspaces: List<UserWorkspace>,
) {

    companion object {
        internal fun validateEmail(email: String): Maybe<String> =
            email.trim().let {
                if (it.matches(simpleEmailPattern)) {
                    Result(it)
                } else {
                    Error(
                        code = UserErrorCodes.REGISTRATION_EMAIL_INVALID,
                        details = null,
                    )
                }
            }


        internal fun create(email: String, name: String): Maybe<User> {
            if (name.isBlank()) {
                return Error(
                    code = UserErrorCodes.REGISTRATION_NAME_BLANK,
                    details = null,
                )
            }

            return validateEmail(email).map {
                User(
                    id = UUID.randomUUID(),
                    email = it,
                    name = name.trim(),
                    workspaces = emptyList(),
                )
            }
        }
    }

    private val numberOfInstalledApps by lazy {
        workspaces.flatMap { it.appInstallations }.count()
    }

    internal fun createWorkspace(name: String): Maybe<User> =
        if (name.isBlank()) {
            Error(
                code = UserErrorCodes.CREATE_WORKSPACE_NAME_BLANK,
                details = null,
            )
        } else {
            Result(
                copy(
                    workspaces = workspaces.plus(
                        UserWorkspace(
                            id = UUID.randomUUID(),
                            name = name.trim(),
                            appInstallations = emptyList()
                        )
                    )
                )
            )
        }

    internal fun renameWorkspace(id: UUID, newName: String): Maybe<User> =
        if (newName.isBlank()) {
            Error(
                code = UserErrorCodes.RENAME_WORKSPACE_NAME_BLANK,
                details = null,
            )
        } else {
            Result(
                copy(
                    workspaces = workspaces.map { existingWorkspace ->
                        if (existingWorkspace.id == id) {
                            existingWorkspace.copy(name = newName.trim())
                        } else {
                            existingWorkspace
                        }
                    }
                )
            )
        }

    // TODO #25 methods for sorting/ordering workspaces

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

        return Result(
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

    internal fun deleteWorkspace(id: UUID): Maybe<User> {
        val workspace = workspaces.firstOrNull { it.id == id }
            ?: return Error(
                code = WorkspaceErrorCodes.NOT_FOUND,
                details = id.toString(),
            )

        return workspace.canBeDeleted().transform {
            Result(
                copy(
                    workspaces = workspaces.filter { it.id != id }
                )
            )
        }
    }

    internal fun canBeDeleted(): Maybe<Unit> {
        return when {
            numberOfInstalledApps > 0 -> Error(
                code = UserErrorCodes.DELETE_INSTALLED_APPS,
                details = numberOfInstalledApps.toString()
            )
            else -> Result(Unit)
        }
    }
}

// TODO #25 ensure values are trimmed (or enforce usage of create function)
data class UserWorkspace(
    val id: UUID,
    val name: String,
    val appInstallations: List<AppInstallation>,
) {

    // TODO #25 check if version exists/is released
    internal fun installApp(appId: UUID, appVersion: Semver): Maybe<UserWorkspace> =
        Result(
            copy(
                appInstallations = appInstallations.plus(
                    AppInstallation(
                        id = UUID.randomUUID(),
                        appId = appId,
                        version = appVersion,
                        nameSupplement = null,
                    )
                )
            )
        )

    // TODO #25 methods for sorting/ordering app installations

    internal fun nameAppInstallation(id: UUID, nameSupplement: String?): Maybe<UserWorkspace> =
        modifyAppInstallation(id) {
            it.copy(nameSupplement = nameSupplement?.trim())
        }

    // TODO #25 check if version exists/is released
    // TODO #5 trigger data update, handle breaking changes
    internal fun updateAppInstallation(id: UUID, newVersion: Semver): Maybe<UserWorkspace> =
        modifyAppInstallation(id) {
            it.copy(version = newVersion)
        }

    private fun modifyAppInstallation(id: UUID, modifier: (AppInstallation) -> AppInstallation): Maybe<UserWorkspace> {
        if (findAppInstallation(id) == null) {
            return Error(
                code = AppInstallationErrorCodes.NOT_FOUND,
                details = id.toString(),
            )
        }

        return Result(
            copy(
                appInstallations = appInstallations.map {
                    if (it.id == id) {
                        modifier(it)
                    } else {
                        it
                    }
                }
            )
        )
    }

    // TODO #5 decide when deleting app is allowed
    internal fun uninstallApp(id: UUID): Maybe<UserWorkspace> {
        val appInstallation = findAppInstallation(id)
            ?: return Error(
                code = AppInstallationErrorCodes.NOT_FOUND,
                details = id.toString(),
            )

        return appInstallation.canBeDeleted().transform {
            Result(
                copy(
                    appInstallations = appInstallations.filterNot { it.id == id }
                )
            )
        }
    }

    private fun findAppInstallation(id: UUID) = appInstallations.firstOrNull { it.id == id }

    // TODO #25 private after user / workspace split
    internal fun canBeDeleted(): Maybe<Unit> = when {
        appInstallations.isNotEmpty() -> {
            Error(
                code = WorkspaceErrorCodes.DELETE_INSTALLED_APPS,
                details = appInstallations.count().toString()
            )
        }
        else -> Result(Unit)
    }
}

// TODO #8 add data sharing options
// TODO #8 think about access for devices / api keys
// TODO #25 ensure values are trimmed (or enforce usage of create function)
data class AppInstallation(
    val id: UUID,
    val appId: UUID,
    val version: Semver,
    val nameSupplement: String?,
) {

    // TODO #5 define rules when to delete app installations. what about the data? what if shared?
    internal fun canBeDeleted(): Maybe<Unit> =
        Error(
            code = AppInstallationErrorCodes.DELETE_NOT_SUPPORTED,
            details = null,
        )
}
