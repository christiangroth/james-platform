package de.chrgroth.james.workspace

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.InvalidInstanceException
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import de.chrgroth.james.foldErrors
import de.chrgroth.james.shrink
import de.chrgroth.james.trimToNull
import de.chrgroth.james.validateNotBlank
import de.chrgroth.james.validateNotNegative
import java.util.UUID

data class Workspace private constructor(
    val id: UUID,
    val userId: UUID,
    private var orderField: Long,
    private var nameField: String,
    val appInstallations: List<AppInstallation>,
) {

    companion object {
        fun create(userId: UUID, order: Long, name: String): Maybe<Workspace> {
            val orderValidation = validateNotNegative(
                value = order,
                codeNegative = WorkspaceErrorCodes.ORDER_NEGATIVE,
            )
            val nameValidation = validateNotBlank(
                value = name,
                codeBlank = WorkspaceErrorCodes.NAME_BLANK,
            )
            val validationErrors = listOf(
                orderValidation, nameValidation
            ).foldErrors<Workspace>().shrink()
            if (validationErrors != null) {
                return validationErrors
            }

            return orderValidation.flatMap { validOrder ->
                nameValidation.map { validName ->
                    Workspace(
                        id = UUID.randomUUID(),
                        userId = userId,
                        orderField = validOrder,
                        nameField = validName,
                        appInstallations = emptyList()
                    )
                }
            }
        }
    }

    // TODO #25 test exception usecase
    init {
        nameField = nameField.trim()

        val orderValidation = validateNotNegative(
            value = order,
            codeNegative = WorkspaceErrorCodes.ORDER_NEGATIVE,
        )
        val nameValidation = validateNotBlank(
            value = name,
            codeBlank = WorkspaceErrorCodes.NAME_BLANK,
        )

        listOf(orderValidation, nameValidation).foldErrors<Workspace>()?.also {
            throw InvalidInstanceException(javaClass.simpleName, it.errors)
        }
    }

    val order get() = orderField
    val name get() = nameField

    internal fun changeOrder(newOrder: Long): Maybe<Workspace> =
        validateNotNegative(
            value = newOrder,
            codeNegative = WorkspaceErrorCodes.ORDER_NEGATIVE,
        ).map { validOrder ->
            copy(orderField = validOrder)
        }

    internal fun changeName(newName: String): Maybe<Workspace> =
        validateNotBlank(
            value = newName,
            codeBlank = WorkspaceErrorCodes.NAME_BLANK,
        ).map { validName ->
            copy(nameField = validName)
        }

    internal fun installApp(appId: UUID, appVersion: Semver): Maybe<Workspace> =
        AppInstallation.create(
            appId = appId,
            version = appVersion,
            nameSupplement = null,
        ).map {
            copy(appInstallations = appInstallations.plus(it))
        }

    internal fun acceptAppMigration(app: AppInstallation): Maybe<Workspace> =
        Result(copy(appInstallations = appInstallations.plus(app)))

    internal fun reorderAppInstallations(order: List<UUID>): Maybe<Workspace> {
        val existingIds = appInstallations.map { it.id }

        val newIds = order.minus(existingIds.toSet())
        val unknownIdsValidation: Error<Workspace>? = if (newIds.isNotEmpty()) {
            Error(
                code = WorkspaceErrorCodes.REORDER_APPS_UNKNOWN_IDS,
                details = newIds.toString(),
            )
        } else null

        val missingIds = existingIds.minus(order.toSet())
        val missingIdsValidation: Error<Workspace>? = if (missingIds.isNotEmpty()) {
            Error(
                code = WorkspaceErrorCodes.REORDER_APPS_MISSING_IDS,
                details = missingIds.toString(),
            )
        } else null

        val validationErrors = listOf(
            unknownIdsValidation, missingIdsValidation
        ).foldErrors<Workspace>().shrink()
        if (validationErrors != null) {
            return validationErrors
        }

        return Result(copy(appInstallations = order.map { orderId ->
            appInstallations.first { it.id == orderId }
        }))
    }

    internal fun nameAppInstallation(id: UUID, nameSupplement: String?): Maybe<Workspace> =
        modifyAppInstallation(id) {
            it.changeNameSupplement(nameSupplement)
        }

    internal fun updateAppInstallation(id: UUID, version: Semver): Maybe<Workspace> =
        modifyAppInstallation(id) {
            it.changeVersion(version)
        }

    private fun modifyAppInstallation(id: UUID, modifier: (AppInstallation) -> Maybe<AppInstallation>): Maybe<Workspace> =
        getAppOrError(id).flatMap {
            modifier(it).map { updatedAppInstallation ->
                copy(
                    appInstallations = appInstallations.map {
                        if (it.id == id) {
                            updatedAppInstallation
                        } else {
                            it
                        }
                    }
                )
            }
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
data class AppInstallation private constructor(
    val id: UUID,
    val appId: UUID,
    val version: Semver,
    private var nameSupplementField: String?,
) {

    companion object {
        fun create(appId: UUID, version: Semver, nameSupplement: String?): Maybe<AppInstallation> {
            return Result(
                AppInstallation(
                    id = UUID.randomUUID(),
                    appId = appId,
                    version = version,
                    nameSupplementField = nameSupplement
                )
            )
        }
    }

    init {
        nameSupplementField = nameSupplementField.trimToNull()
    }

    val nameSupplement get() = nameSupplementField

    internal fun changeNameSupplement(nameSupplement: String?): Maybe<AppInstallation> =
        Result(copy(nameSupplementField = nameSupplement))

    // TODO #5 trigger data update, handle breaking changes
    internal fun changeVersion(version: Semver): Maybe<AppInstallation> =
        Result(copy(version = version))

    // TODO #5 define rules when to delete app installations. what about the data? what if shared?
    internal fun verifyDeletion(): Maybe<Unit> =
        Error(
            code = WorkspaceErrorCodes.APP_UNINSTALL_NOT_SUPPORTED,
            details = null,
        )
}
