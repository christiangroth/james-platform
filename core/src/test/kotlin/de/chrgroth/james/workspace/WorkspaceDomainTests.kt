package de.chrgroth.james.workspace

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.app.AppQueryPersistencePort
import de.chrgroth.james.expectError
import de.chrgroth.james.expectErrors
import de.chrgroth.james.expectSuccess
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

// TODO #25 test moving AppInstallation between Workspace instances

// TODO #25 test install app with unreleased/non-existent version
// TODO #25 test update app with unreleased/non-existent version

class WorkspaceDomainTests {

    private val existingUserId = UUID.randomUUID()
    private val existingWorkspaceOne = Workspace.create(existingUserId, 0, "Existing One").expectSuccess()
    private val existingWorkspaceTwo = Workspace.create(existingUserId, 1, "Existing Two").expectSuccess()
    private val existingOneId = existingWorkspaceOne.id
    private val existingTwoId = existingWorkspaceTwo.id
    private val unknownId = UUID.randomUUID()

    private lateinit var appQueryPersistence: AppQueryPersistencePort
    private lateinit var queryPersistence: WorkspaceQueryPersistencePort
    private lateinit var commandPersistence: WorkspaceCommandPersistencePort
    private lateinit var port: WorkspaceCommandPort

    @BeforeEach
    internal fun initialize() {
        queryPersistence = mockk<WorkspaceQueryPersistencePort>().also {
            every { it.getOrError(existingOneId) }.returns(Maybe.Result(existingWorkspaceOne))
            every { it.getOrError(existingTwoId) }.returns(Maybe.Result(existingWorkspaceTwo))
            every { it.getOrError(unknownId) }.returns(Error(WorkspaceErrorCodes.NOT_FOUND, unknownId.toString()))

            every { it.findForUser(any()) }.returns(Maybe.Result(listOf()))
            every { it.findForUser(existingWorkspaceOne.userId) }
                .returns(Maybe.Result(listOf(existingWorkspaceOne, existingWorkspaceTwo)))
        }

        commandPersistence = mockk<WorkspaceCommandPersistencePort>().also {
            every { it.upsert(any()) }.answers { Maybe.Result(this.args[0] as Workspace) }
        }

        appQueryPersistence = mockk<AppQueryPersistencePort>()

        port = WorkspaceCommandAdapter(appQueryPersistence, queryPersistence, commandPersistence)
    }

    @Test
    fun `new valid workspace`() {
        port.createWorkspace(existingUserId, "  A New Workspace   \t").expectSuccess()
        verifySequence {
            queryPersistence.findForUser(existingUserId)
            commandPersistence.upsert(withArg {
                val workspace = this.actual as Workspace
                assertThat(workspace.id).isNotIn(existingOneId, existingTwoId, unknownId)
                assertThat(workspace.userId).isEqualTo(existingUserId)
                assertThat(workspace.order).isEqualTo(2)
                assertThat(workspace.name).isEqualTo("A New Workspace")
                assertThat(workspace.appInstallations).isEmpty()
            })
        }
    }

    @Test
    fun `first valid workspace for user`() {
        val userId = UUID.randomUUID()
        port.createWorkspace(userId, "My First Workspace").expectSuccess()
        verifySequence {
            queryPersistence.findForUser(userId)
            commandPersistence.upsert(withArg {
                assertThat((actual as Workspace).order).isEqualTo(0)
            })
        }
    }

    @Test
    fun `new workspace with blank name`() {
        port.createWorkspace(existingUserId, " ").expectError(
            code = WorkspaceErrorCodes.NAME_BLANK,
            details = null,
        )
    }

    @Test
    fun `reorder user workspaces`() {
        port.reorderWorkspaces(existingUserId, listOf(existingTwoId, existingOneId)).expectSuccess()
        verifySequence {
            queryPersistence.findForUser(existingUserId)
            commandPersistence.upsert(withArg {
                val workspace = actual as Workspace
                assertThat(workspace.id).isEqualTo(existingTwoId)
                assertThat(workspace.order).isEqualTo(0)
            })
            commandPersistence.upsert(withArg {
                val workspace = actual as Workspace
                assertThat(workspace.id).isEqualTo(existingOneId)
                assertThat(workspace.order).isEqualTo(1)
            })
        }
    }

    @Test
    fun `reorder user workspaces with unknown id`() {
        port.reorderWorkspaces(existingUserId, listOf(existingTwoId, existingOneId, unknownId)).expectError(
            code = WorkspaceErrorCodes.REORDER_WORKSPACES_UNKNOWN_IDS,
            details = listOf(unknownId).toString(),
        )
        verifySequence {
            queryPersistence.findForUser(existingUserId)
        }
    }

    @Test
    fun `reorder user workspaces with missing id`() {
        port.reorderWorkspaces(existingUserId, listOf(existingTwoId)).expectError(
            code = WorkspaceErrorCodes.REORDER_WORKSPACES_MISSING_IDS,
            details = listOf(existingOneId).toString(),
        )
        verifySequence {
            queryPersistence.findForUser(existingUserId)
        }
    }

    @Test
    fun `reorder user workspaces with missing and unknown id`() {
        port.reorderWorkspaces(existingUserId, listOf(unknownId)).expectErrors(
            Error(
                code = WorkspaceErrorCodes.REORDER_WORKSPACES_MISSING_IDS,
                details = listOf(existingOneId, existingTwoId).toString(),
            ),
            Error(
                code = WorkspaceErrorCodes.REORDER_WORKSPACES_UNKNOWN_IDS,
                details = listOf(unknownId).toString()
            )
        )
        verifySequence {
            queryPersistence.findForUser(existingUserId)
        }
    }

    @Test
    fun `rename workspace`() {
        port.renameWorkspace(existingOneId, "A New Name").expectSuccess()
        verifySequence {
            queryPersistence.getOrError(existingOneId)
            commandPersistence.upsert(withArg {
                assertThat((actual as Workspace).name).isEqualTo("A New Name")
            })
        }
    }

    @Test
    fun `rename workspace to blank name`() {
        port.renameWorkspace(existingOneId, " ").expectError(
            code = WorkspaceErrorCodes.NAME_BLANK,
            details = null,
        )
    }

    @Test
    fun `install app`() {
        val appId = UUID.randomUUID()
        val workspace = createWorkspace().installApp(appId, Semver("1.0.0")).expectSuccess()
        assertThat(workspace.appInstallations).hasSize(1)
        assertThat(workspace.appInstallations.first().appId).isEqualTo(appId)
        assertThat(workspace.appInstallations.first().version).isEqualTo(Semver("1.0.0"))
        assertThat(workspace.appInstallations.first().nameSupplement).isNull()
    }

    @Test
    fun `install app twice`() {
        val appId = UUID.randomUUID()
        val workspace = createWorkspace()
            .installApp(appId, Semver("1.0.0")).expectSuccess()
            .installApp(appId, Semver("1.0.0")).expectSuccess()
        assertThat(workspace.appInstallations).hasSize(2)

        val firstInstallation = workspace.appInstallations.toList()[0]
        assertThat(firstInstallation.appId).isEqualTo(appId)
        assertThat(firstInstallation.version).isEqualTo(Semver("1.0.0"))

        val secondInstallation = workspace.appInstallations.toList()[1]
        assertThat(secondInstallation.appId).isEqualTo(appId)
        assertThat(secondInstallation.version).isEqualTo(Semver("1.0.0"))

        assertThat(firstInstallation.id).isNotEqualTo(secondInstallation.id)
    }

    @Test
    fun `accept app migration`() {
        val appId = UUID.randomUUID()
        val workspace = createWorkspace().acceptAppMigration(
            AppInstallation.create(
                appId = appId,
                version = Semver("1.0.0"),
                nameSupplement = null,
            ).expectSuccess()
        ).expectSuccess()
        assertThat(workspace.appInstallations).hasSize(1)
        assertThat(workspace.appInstallations.first().appId).isEqualTo(appId)
        assertThat(workspace.appInstallations.first().version).isEqualTo(Semver("1.0.0"))
        assertThat(workspace.appInstallations.first().nameSupplement).isNull()
    }

    @Test
    fun `reorder apps`() {
        val appIdOne = UUID.randomUUID()
        val appIdTwo = UUID.randomUUID()
        val workspace = createWorkspace()
            .installApp(appIdOne, Semver("1.0.0")).expectSuccess()
            .installApp(appIdTwo, Semver("1.0.0")).expectSuccess()
        assertThat(workspace.appInstallations.map { it.appId }).isEqualTo(listOf(appIdOne, appIdTwo))

        val appInstallationIdOne = workspace.appInstallations[0].id
        val appInstallationIdTwo = workspace.appInstallations[1].id
        val updatedWorkspace = workspace.reorderAppInstallations(
            listOf(appInstallationIdTwo, appInstallationIdOne)
        ).expectSuccess()
        assertThat(updatedWorkspace.appInstallations.map { it.appId }).isEqualTo(listOf(appIdTwo, appIdOne))
    }

    @Test
    fun `reorder apps with unknown id`() {
        val appIdOne = UUID.randomUUID()
        val appIdTwo = UUID.randomUUID()
        val workspace = createWorkspace()
            .installApp(appIdOne, Semver("1.0.0")).expectSuccess()
            .installApp(appIdTwo, Semver("1.0.0")).expectSuccess()
        assertThat(workspace.appInstallations.map { it.appId }).isEqualTo(listOf(appIdOne, appIdTwo))

        val appInstallationIdOne = workspace.appInstallations[0].id
        val appInstallationIdTwo = workspace.appInstallations[1].id
        val appInstallationIdUnknown = UUID.randomUUID()
        workspace.reorderAppInstallations(
            listOf(appInstallationIdTwo, appInstallationIdOne, appInstallationIdUnknown)
        ).expectError(
            code = WorkspaceErrorCodes.REORDER_APPS_UNKNOWN_IDS,
            details = setOf(appInstallationIdUnknown).toString()
        )
    }

    @Test
    fun `reorder apps with missing id`() {
        val appIdOne = UUID.randomUUID()
        val appIdTwo = UUID.randomUUID()
        val workspace = createWorkspace()
            .installApp(appIdOne, Semver("1.0.0")).expectSuccess()
            .installApp(appIdTwo, Semver("1.0.0")).expectSuccess()
        assertThat(workspace.appInstallations.map { it.appId }).isEqualTo(listOf(appIdOne, appIdTwo))

        val appInstallationIdOne = workspace.appInstallations[0].id
        val appInstallationIdTwo = workspace.appInstallations[1].id
        workspace.reorderAppInstallations(
            listOf(appInstallationIdOne)
        ).expectError(
            code = WorkspaceErrorCodes.REORDER_APPS_MISSING_IDS,
            details = setOf(appInstallationIdTwo).toString()
        )
    }

    @Test
    fun `reorder apps with multiple errors`() {
        val appIdOne = UUID.randomUUID()
        val appIdTwo = UUID.randomUUID()
        val workspace = createWorkspace()
            .installApp(appIdOne, Semver("1.0.0")).expectSuccess()
            .installApp(appIdTwo, Semver("1.0.0")).expectSuccess()
        assertThat(workspace.appInstallations.map { it.appId }).isEqualTo(listOf(appIdOne, appIdTwo))

        val appInstallationIdOne = workspace.appInstallations[0].id
        val appInstallationIdTwo = workspace.appInstallations[1].id
        val appInstallationIdUnknown = UUID.randomUUID()
        workspace.reorderAppInstallations(
            listOf(appInstallationIdOne, appInstallationIdUnknown)
        ).expectErrors(
            Error(
                code = WorkspaceErrorCodes.REORDER_APPS_MISSING_IDS,
                details = setOf(appInstallationIdTwo).toString(),
            ),
            Error(
                code = WorkspaceErrorCodes.REORDER_APPS_UNKNOWN_IDS,
                details = setOf(appInstallationIdUnknown).toString()
            )
        )
    }

    @Test
    fun `name app installation`() {
        val appId = UUID.randomUUID()
        val workspace = createWorkspace().installApp(appId, Semver("1.0.0")).expectSuccess()
        val appInstallationId = workspace.appInstallations.first().id
        val updatedWorkspace = workspace.nameAppInstallation(appInstallationId, "NAMED").expectSuccess()
        assertThat(updatedWorkspace.appInstallations).hasSize(1)
        assertThat(updatedWorkspace.appInstallations.first().id).isEqualTo(appInstallationId)
        assertThat(updatedWorkspace.appInstallations.first().nameSupplement).isEqualTo("NAMED")
    }

    @Test
    fun `update app installation`() {
        val appId = UUID.randomUUID()
        val workspace = createWorkspace().installApp(appId, Semver("1.0.0")).expectSuccess()
        val appInstallationId = workspace.appInstallations.first().id
        val updatedWorkspace = workspace.updateAppInstallation(appInstallationId, Semver("2.0.0")).expectSuccess()
        assertThat(updatedWorkspace.appInstallations).hasSize(1)
        assertThat(updatedWorkspace.appInstallations.first().id).isEqualTo(appInstallationId)
        assertThat(updatedWorkspace.appInstallations.first().version).isEqualTo(Semver("2.0.0"))
    }

    @Test
    fun `uninstall app`() {
        val appId = UUID.randomUUID()
        val workspace = createWorkspace().installApp(appId, Semver("1.0.0")).expectSuccess()
        val appInstallationId = workspace.appInstallations.first().id
        workspace.uninstallApp(appInstallationId).expectError(
            code = WorkspaceErrorCodes.APP_UNINSTALL_NOT_SUPPORTED,
            details = null,
        )
    }

    @Test
    fun `uninstall unknown app`() {
        val appId = UUID.randomUUID()
        val workspace = createWorkspace().installApp(appId, Semver("1.0.0")).expectSuccess()
        val unknownAppInstallationId = UUID.randomUUID()
        workspace.uninstallApp(unknownAppInstallationId).expectError(
            code = WorkspaceErrorCodes.APP_INSTALLATION_NOT_FOUND,
            details = unknownAppInstallationId.toString(),
        )
    }

    @Test
    fun `delete empty workspace`() {
        createWorkspace().verifyDeletion().expectSuccess()
    }

    @Test
    fun `delete not empty workspace`() {
        createWorkspace()
            .installApp(UUID.randomUUID(), Semver("1.0.0")).expectSuccess()
            .verifyDeletion().expectError(
                code = WorkspaceErrorCodes.DELETE_WORKSPACE_INSTALLED_APPS,
                details = "1",
            )
    }

    private fun createWorkspace() = UUID.randomUUID().let {
        Workspace.create(it, 0, it.toString()).expectSuccess()
    }
}
