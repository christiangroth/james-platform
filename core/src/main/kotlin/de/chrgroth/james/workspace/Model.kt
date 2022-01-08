package de.chrgroth.james.workspace

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import de.chrgroth.james.foldAndShrink
import de.chrgroth.james.foldErrors
import de.chrgroth.james.throwOnError
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
        private fun validateOrder(order: Long) =
            validateNotNegative(order, WorkspaceErrorCodes.ORDER_NEGATIVE)

        private fun validateName(name: String) =
            validateNotBlank(name, WorkspaceErrorCodes.NAME_BLANK)

        fun create(userId: UUID, order: Long, name: String): Maybe<Workspace> {
            val orderValidation = validateOrder(order)
            val nameValidation = validateName(name)
            return listOf<Maybe<out Any>>(orderValidation, nameValidation).foldAndShrink()
                ?: orderValidation.flatMap { validOrder ->
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

    init {
        nameField = nameField.trim()

        listOf(validateOrder(order), validateName(nameField)).foldErrors<Workspace>().throwOnError(javaClass.simpleName)
    }

    val order get() = orderField
    val name get() = nameField

    internal fun changeOrder(order: Long): Maybe<Workspace> =
        validateOrder(order).map { validOrder ->
            copy(orderField = validOrder)
        }

    internal fun changeName(name: String): Maybe<Workspace> =
        validateName(name).map { validName ->
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

    internal fun acceptAppDemigration(app: AppInstallation): Maybe<Workspace> =
        Result(copy(appInstallations = appInstallations.minus(app)))

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

        return listOf(unknownIdsValidation, missingIdsValidation).foldAndShrink()
            ?: Result(copy(appInstallations = order.map { orderId ->
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

    internal fun modifyAppInstallation(id: UUID, modifier: (AppInstallation) -> Maybe<AppInstallation>): Maybe<Workspace> =
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
            app.verifyDeletion()
        }.map {
            copy(appInstallations = appInstallations.filterNot { it.id == id })
        }

    internal fun getAppOrError(appInstallationId: UUID): Maybe<AppInstallation> =
        appInstallations.firstOrNull { it.id == appInstallationId }.let { app ->
            if (app == null) {
                Error(
                    code = WorkspaceErrorCodes.INSTALLATION_NOT_FOUND,
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
        if (this.version >= version) {
            Error(
                code = WorkspaceErrorCodes.DOWNGRADE_NOT_SUPPORTED,
                details = "${this.version} >= $version",
            )
        } else {
            Result(copy(version = version))
        }

    // TODO #5 define rules when to delete app installations. what about the data? what if shared?
    internal fun verifyDeletion(): Maybe<Unit> =
        Error(
            code = WorkspaceErrorCodes.UNINSTALL_NOT_SUPPORTED,
            details = null,
        )
}
