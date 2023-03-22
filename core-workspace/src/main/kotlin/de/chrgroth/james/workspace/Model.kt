package de.chrgroth.james.workspace

import arrow.core.Validated
import arrow.core.ValidatedNel
import arrow.core.andThen
import com.github.glwithu06.semver.Semver
import com.sksamuel.tribune.core.Parser
import com.sksamuel.tribune.core.compose
import de.chrgroth.james.DomainError
import de.chrgroth.james.createValidation
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
            WorkspaceDomainErrorCodes.ORDER_NEGATIVE
        )

        private val nameParser = notBlankParser(
            WorkspaceDomainErrorCodes.NAME_BLANK
        )

        private data class WorkspaceParserInput(val order: Long, val name: String)

        fun create(
            id: UUID = UUID.randomUUID(),
            userId: UUID,
            order: Long,
            name: String,
            appInstallations: List<AppInstallation> = emptyList(),
        ): ValidatedNel<DomainError, Workspace> {

            val workspaceParser: Parser<WorkspaceParserInput, Workspace, DomainError> = Parser
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

    internal fun changeOrder(order: Long): ValidatedNel<DomainError, Workspace> =
        create(id, userId, order, nameField, appInstallations)

    internal fun changeName(name: String): ValidatedNel<DomainError, Workspace> =
        create(id, userId, orderField, name, appInstallations)

    internal fun installApp(appId: UUID, appVersion: Semver): ValidatedNel<DomainError, Workspace> =
        AppInstallation.create(
            appId = appId,
            version = appVersion,
            nameSupplement = null,
        ).andThen {
            create(id, userId, orderField, nameField, appInstallations.plus(it))
        }

    internal fun acceptAppMigration(app: AppInstallation): ValidatedNel<DomainError, Workspace> =
        create(id, userId, orderField, nameField, appInstallations.plus(app))

    internal fun acceptAppDemigration(app: AppInstallation): ValidatedNel<DomainError, Workspace> =
        create(id, userId, orderField, nameField, appInstallations.minus(app))

    internal fun reorderAppInstallations(order: List<UUID>): ValidatedNel<DomainError, Workspace> {
        val existingIds = appInstallations.map { it.id }

        val newIds = order.minus(existingIds.toSet())
        val newIdsValidation = createValidation(
            errorCondition = newIds.isNotEmpty(),
            domainErrorCode = WorkspaceDomainErrorCodes.REORDER_APPS_UNKNOWN_IDS,
            errorDetails = newIds.toString(),
        ) {}

        val missingIds = existingIds.minus(order.toSet())
        val missingIdsValidation = createValidation(
            errorCondition = missingIds.isNotEmpty(),
            domainErrorCode = WorkspaceDomainErrorCodes.REORDER_APPS_MISSING_IDS,
            errorDetails = missingIds.toString(),
        ) {}

        return listOf(newIdsValidation, missingIdsValidation).reduceWithFirstValue().andThen {
            create(id, userId, orderField, nameField, order.map { orderId ->
                appInstallations.first { it.id == orderId }
            })
        }
    }

    internal fun nameAppInstallation(id: UUID, nameSupplement: String?): ValidatedNel<DomainError, Workspace> =
        modifyAppInstallation(id) {
            it.changeNameSupplement(nameSupplement)
        }

    internal fun updateAppInstallation(id: UUID, version: Semver): ValidatedNel<DomainError, Workspace> =
        modifyAppInstallation(id) {
            it.changeVersion(version)
        }

    private fun modifyAppInstallation(
        appInstallationId: UUID,
        modifier: (AppInstallation) -> ValidatedNel<DomainError, AppInstallation>,
    ): ValidatedNel<DomainError, Workspace> =
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

    // TODO #2 decide when deleting app is allowed
    internal fun uninstallApp(id: UUID): ValidatedNel<DomainError, Workspace> =
        getAppInstallationOrError(id).andThen { app ->
            app.verifyDeletion()
        }.andThen {
            create(id, userId, orderField, nameField, appInstallations.filterNot { it.id == id })
        }

    internal fun getAppInstallationOrError(appInstallationId: UUID): ValidatedNel<DomainError, AppInstallation> =
        appInstallations.firstOrNull { it.id == appInstallationId }.let { app ->
            createValidation(
                errorCondition = app == null,
                domainErrorCode = WorkspaceDomainErrorCodes.INSTALLATION_NOT_FOUND,
                errorDetails = appInstallationId.toString(),
            ) { app!! }
        }

    internal fun verifyDeletion(): ValidatedNel<DomainError, Unit> =
        createValidation(
            errorCondition = appInstallations.isNotEmpty(),
            domainErrorCode = WorkspaceDomainErrorCodes.DELETE_WORKSPACE_INSTALLED_APPS,
            errorDetails = appInstallations.count().toString()
        ) {}
}

// TODO #1 add data sharing options
// TODO #1 think about access for devices / api keys
data class AppInstallation private constructor(
    val id: UUID,
    val appId: UUID,
    val version: Semver,
    private var nameSupplementField: String?,
) {

    companion object {
        fun create(id: UUID = UUID.randomUUID(), appId: UUID, version: Semver, nameSupplement: String?): ValidatedNel<DomainError, AppInstallation> =
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

    internal fun changeNameSupplement(nameSupplement: String?): ValidatedNel<DomainError, AppInstallation> =
        create(id, appId, version, nameSupplement)

    // TODO #2 trigger data update, handle breaking changes
    internal fun changeVersion(version: Semver): ValidatedNel<DomainError, AppInstallation> =
        createValidation(
            this.version >= version,
            WorkspaceDomainErrorCodes.DOWNGRADE_NOT_SUPPORTED,
            "${this.version} >= $version",
        ) {}
            .andThen {
                create(id, appId, version, nameSupplementField)
            }

    // TODO #2 define rules when to delete app installations. what about the data? what if shared?
    internal fun verifyDeletion(): ValidatedNel<DomainError, Unit> =
        Validated.invalidNel(
            DomainError(
                code = WorkspaceDomainErrorCodes.UNINSTALL_NOT_SUPPORTED,
                details = null,
            )
        )
}
