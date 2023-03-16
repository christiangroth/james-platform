package de.chrgroth.james.workspace

import arrow.core.Validated
import arrow.core.ValidatedNel
import arrow.core.andThen
import com.github.glwithu06.semver.Semver
import com.sksamuel.tribune.core.Parser
import com.sksamuel.tribune.core.compose
import de.chrgroth.james.Error
import de.chrgroth.james.notBlankParser
import de.chrgroth.james.notNegativeLongParser
import de.chrgroth.james.reduceWithFirstValue
import de.chrgroth.james.trimToNull
import java.util.UUID

data class Workspace private constructor(
    val id: UUID,
    val userId: UUID,
    private var orderField: Long,
    private var nameField: String,
    val appInstallations: List<AppInstallation>,
) {

    companion object {

        private val orderParser = notNegativeLongParser(
            WorkspaceErrorCodes.ORDER_NEGATIVE
        )

        private val nameParser = notBlankParser(
            WorkspaceErrorCodes.NAME_BLANK
        )

        private data class WorkspaceParserInput(val order: Long, val name: String)

        fun create(
            id: UUID = UUID.randomUUID(),
            userId: UUID,
            order: Long,
            name: String,
            appInstallations: List<AppInstallation> = emptyList(),
        ): ValidatedNel<Error, Workspace> {

            val workspaceParser: Parser<WorkspaceParserInput, Workspace, Error> = Parser
                .compose(
                    orderParser.contramap { it.order.toString() },
                    nameParser.contramap { it.name },
                ) { validOrder, validName ->
                    Workspace(
                        id = id,
                        userId = userId,
                        orderField = validOrder,
                        nameField = validName,
                        appInstallations = appInstallations,
                    )
                }

            return workspaceParser.parse(WorkspaceParserInput(order, name))
        }
    }

    val order get() = orderField
    val name get() = nameField

    internal fun changeOrder(order: Long): ValidatedNel<Error, Workspace> = create(id, userId, order, nameField, appInstallations)
    internal fun changeName(name: String): ValidatedNel<Error, Workspace> = create(id, userId, orderField, name, appInstallations)

    internal fun installApp(appId: UUID, appVersion: Semver): ValidatedNel<Error, Workspace> =
        AppInstallation.create(
            appId = appId,
            version = appVersion,
            nameSupplement = null,
        ).andThen {
            create(id, userId, orderField, nameField, appInstallations.plus(it))
        }

    internal fun acceptAppMigration(app: AppInstallation): ValidatedNel<Error, Workspace> =
        create(id, userId, orderField, nameField, appInstallations.plus(app))

    internal fun acceptAppDemigration(app: AppInstallation): ValidatedNel<Error, Workspace> =
        create(id, userId, orderField, nameField, appInstallations.minus(app))

    internal fun reorderAppInstallations(order: List<UUID>): ValidatedNel<Error, Workspace> {
        val existingIds = appInstallations.map { it.id }

        val newIds = order.minus(existingIds.toSet())
        val newIdsValidation: ValidatedNel<Error, Unit> = if (newIds.isNotEmpty()) {
            Validated.invalidNel(
                Error(
                    code = WorkspaceErrorCodes.REORDER_APPS_UNKNOWN_IDS,
                    details = newIds.toString(),
                )
            )
        } else Validated.validNel(Unit)

        val missingIds = existingIds.minus(order.toSet())
        val missingIdsValidation: ValidatedNel<Error, Unit> = if (missingIds.isNotEmpty()) {
            return Validated.invalidNel(
                Error(
                    code = WorkspaceErrorCodes.REORDER_APPS_MISSING_IDS,
                    details = missingIds.toString(),
                )
            )
        } else Validated.validNel(Unit)

        return listOf(newIdsValidation, missingIdsValidation).reduceWithFirstValue().andThen {
            create(id, userId, orderField, nameField, order.map { orderId ->
                appInstallations.first { it.id == orderId }
            })
        }
    }

    internal fun nameAppInstallation(id: UUID, nameSupplement: String?): ValidatedNel<Error, Workspace> =
        modifyAppInstallation(id) {
            it.changeNameSupplement(nameSupplement)
        }

    internal fun updateAppInstallation(id: UUID, version: Semver): ValidatedNel<Error, Workspace> =
        modifyAppInstallation(id) {
            it.changeVersion(version)
        }

    private fun modifyAppInstallation(
        appInstallationId: UUID,
        modifier: (AppInstallation) -> ValidatedNel<Error, AppInstallation>,
    ): ValidatedNel<Error, Workspace> =
        getAppInstallationOrError(appInstallationId).andThen {
            modifier(it).andThen { updatedAppInstallation ->
                create(id, userId, orderField, nameField, appInstallations.map {
                    if (it.id == appInstallationId) {
                        updatedAppInstallation
                    } else {
                        it
                    }
                })
            }
        }

    // TODO #5 decide when deleting app is allowed
    internal fun uninstallApp(id: UUID): ValidatedNel<Error, Workspace> =
        getAppInstallationOrError(id).andThen { app ->
            app.verifyDeletion()
        }.andThen {
            create(id, userId, orderField, nameField, appInstallations.filterNot { it.id == id })
        }

    internal fun getAppInstallationOrError(appInstallationId: UUID): ValidatedNel<Error, AppInstallation> =
        appInstallations.firstOrNull { it.id == appInstallationId }.let { app ->
            if (app == null) {
                Validated.invalidNel(
                    Error(
                        code = WorkspaceErrorCodes.INSTALLATION_NOT_FOUND,
                        details = appInstallationId.toString(),
                    )
                )
            } else {
                Validated.validNel(app)
            }
        }

    internal fun verifyDeletion(): ValidatedNel<Error, Unit> = when {
        appInstallations.isNotEmpty() -> {
            Validated.invalidNel(
                Error(
                    code = WorkspaceErrorCodes.DELETE_WORKSPACE_INSTALLED_APPS,
                    details = appInstallations.count().toString()
                )
            )
        }

        else -> Validated.validNel(Unit)
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
        fun create(id: UUID = UUID.randomUUID(), appId: UUID, version: Semver, nameSupplement: String?): ValidatedNel<Error, AppInstallation> =
            Validated.validNel(
                AppInstallation(
                    id = id,
                    appId = appId,
                    version = version,
                    nameSupplementField = nameSupplement
                )
            )
    }

    init {
        nameSupplementField = nameSupplementField.trimToNull()
    }

    val nameSupplement get() = nameSupplementField

    internal fun changeNameSupplement(nameSupplement: String?): ValidatedNel<Error, AppInstallation> =
        create(id, appId, version, nameSupplement)

    // TODO #5 trigger data update, handle breaking changes
    internal fun changeVersion(version: Semver): ValidatedNel<Error, AppInstallation> =
        if (this.version >= version) {
            Validated.invalidNel(
                Error(
                    code = WorkspaceErrorCodes.DOWNGRADE_NOT_SUPPORTED,
                    details = "${this.version} >= $version",
                )
            )
        } else {
            create(id, appId, version, nameSupplementField)
        }

    // TODO #5 define rules when to delete app installations. what about the data? what if shared?
    internal fun verifyDeletion(): ValidatedNel<Error, Unit> =
        Validated.invalidNel(
            Error(
                code = WorkspaceErrorCodes.UNINSTALL_NOT_SUPPORTED,
                details = null,
            )
        )
}
