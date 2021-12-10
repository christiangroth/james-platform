package de.chrgroth.james.workspace

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import java.util.UUID

// TODO #25 ensure trimmed values / enforce usage of create function (https://youtrack.jetbrains.com/issue/KT-11914)
data class Workspace(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val appInstallations: List<AppInstallation>,
) {

    companion object {

        internal fun create(userId: UUID, name: String): Maybe<Workspace> =
            if (name.isBlank()) {
                Error(
                    code = WorkspaceErrorCodes.CREATE_WORKSPACE_NAME_BLANK,
                    details = null,
                )
            } else {
                Result(
                    Workspace(
                        id = UUID.randomUUID(),
                        userId = userId,
                        name = name.trim(),
                        appInstallations = emptyList()
                    )
                )
            }
    }

    internal fun rename(newName: String): Maybe<Workspace> =
        if (newName.isBlank()) {
            Error(
                code = WorkspaceErrorCodes.RENAME_WORKSPACE_NAME_BLANK,
                details = null,
            )
        } else {
            Result(
                copy(name = newName.trim())
            )
        }

    internal fun installApp(appId: UUID, appVersion: Semver): Maybe<Workspace> =
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

    // TODO #25 better naming
    // TODO #25 test
    internal fun accommodateApp(app: AppInstallation): Maybe<Workspace> =
        Result(copy(appInstallations = appInstallations.plus(app)))

    // TODO #25 better naming
    // TODO #25 test
    internal fun removeApp(app: AppInstallation): Maybe<Workspace> =
        Result(copy(appInstallations = appInstallations.minus(app)))

    // TODO #25 methods for sorting/ordering app installations

    internal fun nameAppInstallation(id: UUID, nameSupplement: String?): Maybe<Workspace> =
        modifyAppInstallation(id) {
            it.copy(nameSupplement = nameSupplement?.trim())
        }

    // TODO #5 trigger data update, handle breaking changes
    internal fun updateAppInstallation(id: UUID, newVersion: Semver): Maybe<Workspace> =
        modifyAppInstallation(id) {
            it.copy(version = newVersion)
        }

    private fun modifyAppInstallation(id: UUID, modifier: (AppInstallation) -> AppInstallation): Maybe<Workspace> =
        getAppOrError(id).map {
            copy(
                appInstallations = appInstallations.map {
                    if (it.id == id) {
                        modifier(it)
                    } else {
                        it
                    }
                }
            )
        }

    // TODO #5 decide when deleting app is allowed
    internal fun uninstallApp(id: UUID): Maybe<Workspace> =
        getAppOrError(id).transform { app ->
            app.canBeDeleted().map {
                copy(appInstallations = appInstallations.filterNot { it.id == id })
            }
        }

    internal fun getAppOrError(appInstallationId: UUID): Maybe<AppInstallation> =
        appInstallations.firstOrNull { it.id == appInstallationId }.let { app ->
            if (app == null) {
                Error(
                    code = WorkspaceErrorCodes.APP_NOT_FOUND,
                    details = appInstallationId.toString(),
                )
            } else {
                Result(app)
            }
        }

    // TODO #25 private after user / workspace split
    internal fun canBeDeleted(): Maybe<Unit> = when {
        appInstallations.isNotEmpty() -> {
            Error(
                code = WorkspaceErrorCodes.DELETE_WORKSPACE_INSTALLED_APPS,
                details = appInstallations.count().toString()
            )
        }
        else -> Result(Unit)
    }
}

// TODO #8 add data sharing options
// TODO #8 think about access for devices / api keys
// TODO #25 ensure trimmed values / enforce usage of create function (https://youtrack.jetbrains.com/issue/KT-11914)
data class AppInstallation(
    val id: UUID,
    val appId: UUID,
    val version: Semver,
    val nameSupplement: String?,
) {

    // TODO #5 define rules when to delete app installations. what about the data? what if shared?
    // TODO #25 private after user / workspace split
    internal fun canBeDeleted(): Maybe<Unit> =
        Error(
            code = WorkspaceErrorCodes.APP_UNINSTALL_NOT_SUPPORTED,
            details = null,
        )
}
