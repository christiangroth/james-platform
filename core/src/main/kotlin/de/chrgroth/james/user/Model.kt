package de.chrgroth.james.user

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import java.util.UUID

// TODO #25 split user and workspace/installed apps domains

private val simpleEmailPattern = Regex(".+@.+\\..+")

data class User(
    val id: UUID,
    val email: String,
    val name: String,
    val workspaces: List<UserWorkspace>,
) {

    companion object {

        // TODO #25 test empty/blank
        internal fun validateEmail(email: String): Maybe<String> {
            return if (email.matches(simpleEmailPattern)) {
                Result(email)
            } else {
                Error(
                    code = UserErrorCodes.REGISTRATION_EMAIL_INVALID,
                    details = "$email does not match $simpleEmailPattern",
                )
            }
        }

        // TODO #25 test empty/blank
        internal fun create(email: String, name: String) = User(
            id = UUID.randomUUID(),
            email = email,
            name = name,
            workspaces = emptyList(),
        )
    }

    // TODO #25 test empty/blank
    internal fun createWorkspace(name: String): Maybe<User> =
        Result(
            copy(
                workspaces = workspaces.plus(
                    UserWorkspace(
                        id = UUID.randomUUID(),
                        name = name,
                        appInstallations = emptyList()
                    )
                )
            )
        )

    // TODO #25 test empty/blank
    internal fun renameWorkspace(id: UUID, newName: String): Maybe<User> =
        Result(
            copy(
                workspaces = workspaces.map { existingWorkspace ->
                    if (existingWorkspace.id == id) {
                        existingWorkspace.copy(name = newName)
                    } else {
                        existingWorkspace
                    }
                }
            )
        )

    // TODO #25 methods for sorting/ordering workspaces

    @Suppress("ReturnCount")
    internal fun moveAppInstallation(workspaceId: UUID, appInstallationId: UUID, newWorkspaceId: UUID): Maybe<User> {
        val sourceWorkspace = workspaces.firstOrNull() { it.id == workspaceId }
            ?: return Error(
                code = WorkspaceErrorCodes.NOT_FOUND,
                details = "Source workspace with id $workspaceId not found",
            )

        val appInstallation = sourceWorkspace.appInstallations.firstOrNull { it.id == appInstallationId }
            ?: return Error(
                code = AppInstallationErrorCodes.NOT_FOUND,
                details = "App installation with version $appInstallationId not found",
            )

        val targetWorkspace = workspaces.firstOrNull { it.id == newWorkspaceId }
            ?: return Error(
                code = WorkspaceErrorCodes.NOT_FOUND,
                details = "Target workspace with id $newWorkspaceId not found",
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
                details = "Workspace with id $id not found",
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
        val numberOfInstalledApps = computeNumberOfInstalledApps()
        return when {
            numberOfInstalledApps > 0 -> Error(
                code = UserErrorCodes.DELETE_INSTALLED_APPS,
                details = if (numberOfInstalledApps > 1)
                    "Deletion not possible, there are still $numberOfInstalledApps app installations"
                else
                    "Deletion not possible, there is still $numberOfInstalledApps app installation"
            )
            else -> Result(Unit)
        }
    }

    // TODO #25 extension val??
    private fun computeNumberOfInstalledApps() = workspaces.flatMap { it.appInstallations }.count()
}

data class UserWorkspace(
    val id: UUID,
    val name: String,
    val appInstallations: List<AppInstallation>,
) {

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
            it.copy(nameSupplement = nameSupplement)
        }

    // TODO #5 check if version exists/is released, trigger data update, handle breaking changes
    internal fun updateAppInstallation(id: UUID, newVersion: Semver): Maybe<UserWorkspace> =
        modifyAppInstallation(id) {
            it.copy(version = newVersion)
        }

    private fun modifyAppInstallation(id: UUID, modifier: (AppInstallation) -> AppInstallation): Maybe<UserWorkspace> {
        if (findAppInstallation(id) == null) {
            return Error(
                code = AppInstallationErrorCodes.NOT_FOUND,
                details = "App installation with id $id not found",
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
                details = "App installation with id $id not found",
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

    internal fun canBeDeleted(): Maybe<Unit> = when {
        appInstallations.isNotEmpty() -> {
            val numberOfInstalledApps = appInstallations.count()
            Error(
                code = WorkspaceErrorCodes.DELETE_INSTALLED_APPS,
                details = if (numberOfInstalledApps > 1)
                    "Deletion not possible, there are still $numberOfInstalledApps app installations"
                else
                    "Deletion not possible, there is still $numberOfInstalledApps app installation"
            )
        }
        else -> Result(Unit)
    }
}

// TODO #8 add data sharing options
// TODO #8 think about access for devices / api keys
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
            details = "Uninstalling apps is currently not supported",
        )
}
