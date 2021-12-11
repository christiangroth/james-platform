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
    val order: Long,
    val name: String,
    val appInstallations: List<AppInstallation>,
) {

    companion object {
        internal fun validateOrder(order: Long): Maybe<Long> =
            if (order >= 0) {
                Result(order)
            } else {
                Error(
                    WorkspaceErrorCodes.ORDER_NEGATIVE,
                    details = order.toString(),
                )
            }

        internal fun validateName(name: String): Maybe<String> =
            if (name.isBlank()) {
                Error(
                    code = WorkspaceErrorCodes.NAME_BLANK,
                    details = null,
                )
            } else {
                Result(name.trim())
            }

        // TODO #25 return all Errors
        internal fun create(userId: UUID, order: Long, name: String): Maybe<Workspace> =
            validateOrder(order).flatMap { validOrder ->
                validateName(name).map { validName ->
                    Workspace(
                        id = UUID.randomUUID(),
                        userId = userId,
                        order = validOrder,
                        name = validName,
                        appInstallations = emptyList()
                    )
                }
            }
    }

    internal fun changeOrder(newOrder: Long): Maybe<Workspace> =
        validateOrder(newOrder).map { validOrder ->
            copy(order = validOrder)
        }

    internal fun changeName(newName: String): Maybe<Workspace> =
        validateName(newName).map { validName ->
            copy(name = validName)
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

    internal fun acceptAppMigration(app: AppInstallation): Maybe<Workspace> =
        Result(copy(appInstallations = appInstallations.plus(app)))

    // TODO #25 return all Errors
    internal fun reorderAppInstallations(order: List<UUID>): Maybe<Workspace> {
        val existingIds = appInstallations.map { it.id }
        val newIds = order.minus(existingIds.toSet())
        val missingIds = existingIds.minus(order.toSet())
        return if (newIds.isNotEmpty()) {
            Error(
                code = WorkspaceErrorCodes.REORDER_APPS_UNKNOWN_IDS,
                details = newIds.toString(),
            )
        } else if (missingIds.isNotEmpty()) {
            Error(
                code = WorkspaceErrorCodes.REORDER_APPS_MISSING_IDS,
                details = missingIds.toString(),
            )
        } else {
            Result(copy(appInstallations = order.map { orderId ->
                appInstallations.first { it.id == orderId }
            }))
        }
    }

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
        getAppOrError(id).flatMap { app ->
            app.verifyDeletion().map {
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

    internal fun verifyDeletion(): Maybe<Unit> = when {
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
    internal fun verifyDeletion(): Maybe<Unit> =
        Error(
            code = WorkspaceErrorCodes.APP_UNINSTALL_NOT_SUPPORTED,
            details = null,
        )
}
