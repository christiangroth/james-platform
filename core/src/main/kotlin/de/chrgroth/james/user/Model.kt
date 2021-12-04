package de.chrgroth.james.user

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import java.util.UUID

private val simpleEmailPattern = Regex(".+@.+\\..+")

data class User(
    val id: UUID,
    val email: String,
    val name: String,
    // TODO #25 may be a list to have a concrete order?
    val workspaces: Set<UserWorkspace> = emptySet(),
) {

    // TODO #3 test
    companion object {
        fun validateEmail(email: String): Maybe<String> {
            return if (email.matches(simpleEmailPattern)) {
                Result(email)
            } else {
                Error(
                    code = UserErrorCodes.REGISTRATION_EMAIL_INVALID,
                    details = "$email does not match $simpleEmailPattern",
                )
            }
        }
    }

    // TODO #3 test
    internal fun createWorkspace(name: String): Maybe<User> =
        Result(
            copy(
                workspaces = workspaces.plus(UserWorkspace(
                    id = UUID.randomUUID(),
                    name = name,
                    apps = emptySet())
                )
            )
        )

    // TODO #3 test
    internal fun renameWorkspace(id: UUID, newName: String): Maybe<User> =
        Result(
            copy(
                workspaces = workspaces.map { existingWorkspace ->
                    if (existingWorkspace.id == id) {
                        existingWorkspace.copy(name = newName)
                    } else {
                        existingWorkspace
                    }
                }.toSet()
            )
        )

    // TODO #3 test
    internal fun moveAppInstallation(workspaceId: UUID, appInstallationId: UUID, newWorkspaceId: UUID): Maybe<User> {
        val sourceWorkspace = workspaces.firstOrNull() { it.id == workspaceId }
            ?: return Error(
                code = WorkspaceErrorCodes.NOT_FOUND,
                details = "Source workspace with id $workspaceId not found",
            )

        val appInstallation = sourceWorkspace.apps.firstOrNull { it.id == appInstallationId }
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
                            apps = it.apps.filterNot { sourceInstallation ->
                                sourceInstallation.id == appInstallationId
                            }.toSet()
                        )
                        targetWorkspace.id -> it.copy(
                            apps = it.apps.plus(appInstallation).toSet()
                        )
                        else -> it
                    }
                }.toSet()
            )
        )
    }

    // TODO #3 test
    internal fun deleteWorkspace(id: UUID): Maybe<User> {
        val workspace = workspaces.firstOrNull { it.id == id }
            ?: return Error(
                code = WorkspaceErrorCodes.NOT_FOUND,
                details = "Workspace with id $id not found",
            )

        return workspace.canBeDeleted().transform {
            Result(
                copy(
                    workspaces = workspaces.filter { it.id != id }.toSet()
                )
            )
        }
    }

    // TODO #3 test
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

    private fun computeNumberOfInstalledApps() = workspaces.flatMap { it.apps }.count()
}

data class UserWorkspace(
    val id: UUID,
    val name: String,
    // TODO #25 may be a list to have a concrete order?
    val apps: Set<AppInstallation>,
) {

    // TODO #3 test
    internal fun installApp(appId: UUID, appVersion: Semver): Maybe<UserWorkspace> =
        Result(
            copy(
                apps = apps.plus(
                    AppInstallation(
                        id = UUID.randomUUID(),
                        appId = appId,
                        version = appVersion,
                        nameSupplement = null,
                        category = null,
                        tags = emptySet(),
                    )).toSet()
            )
        )

    // TODO #3 trigger data update, handle breaking changes
    // TODO #3 test
    internal fun updateAppInstallation(id: UUID, newVersion: Semver): Maybe<UserWorkspace> =
        modifyAppInstallation(id) {
            it.copy(version = newVersion)
        }

    // TODO #3 test
    internal fun nameAppInstallation(id: UUID, nameSupplement: String?): Maybe<UserWorkspace> =
        modifyAppInstallation(id) {
            it.copy(nameSupplement = nameSupplement)
        }

    // TODO #3 test
    internal fun categorizeAppInstallation(id: UUID, category: String?): Maybe<UserWorkspace> =
        modifyAppInstallation(id) {
            it.copy(category = category)
        }

    // TODO #3 test
    internal fun tagAppInstallation(id: UUID, tags: Set<String>): Maybe<UserWorkspace> =
        modifyAppInstallation(id) {
            it.copy(tags = tags)
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
                apps = apps.map {
                    if (it.id == id) {
                        modifier(it)
                    } else {
                        it
                    }
                }.toSet()
            )
        )
    }

    // TODO #3 test
    internal fun uninstallApp(id: UUID): Maybe<UserWorkspace> {
        val appInstallation = findAppInstallation(id)
            ?: return Error(
                code = AppInstallationErrorCodes.NOT_FOUND,
                details = "App installation with id $id not found",
            )

        return appInstallation.canBeDeleted().transform {
            Result(
                copy(
                    apps = apps.filterNot { it.id == id }.toSet()
                )
            )
        }
    }

    private fun findAppInstallation(id: UUID) = apps.firstOrNull { it.id == id }

    // TODO #3 test
    internal fun canBeDeleted(): Maybe<Unit> = when {
        apps.isNotEmpty() -> {
            val numberOfInstalledApps = apps.count()
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
    val nameSupplement: String? = null,
    val category: String? = null,
    val tags: Set<String> = emptySet(),
) {

    // TODO #3 test
    // TODO #3 define rules when to delete app installations. what about the data? what if shared?
    internal fun canBeDeleted(): Maybe<Unit> =
        Error(
            code = AppInstallationErrorCodes.DELETE_NOT_SUPPORTED,
            details = "Uninstalling apps it currently not supported"
        )
}
