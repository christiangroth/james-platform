package de.chrgroth.james.workspace

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.app.AppQueryPersistencePort
import de.chrgroth.james.app.AppVersion
import de.chrgroth.james.app.AppVersionChangeType
import de.chrgroth.james.app.AppVersionDraft
import de.chrgroth.james.app.AppVersionReleaseNotes
import de.chrgroth.james.expectError
import de.chrgroth.james.expectErrors
import de.chrgroth.james.expectSuccess
import io.mockk.MockKVerificationScope
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

// TODO #25 test install app with unreleased/non-existent version
// TODO #25 test update app with unreleased/non-existent version

class WorkspaceDomainTests {

    private val existingAppId = UUID.randomUUID()
    private val existingAppVersionId = Semver("1.0.0")
    private val unknownAppVersionId = Semver("1.2.3")
    private val existingAppVersion = AppVersion.create(
        releaseNotes = AppVersionReleaseNotes.create(AppVersionChangeType.FEATURE, "First Release").expectSuccess(),
        developmentVersion = AppVersionDraft.create().expectSuccess(),
        latestVersion = null,
    ).expectSuccess()

    private val existingUserId = UUID.randomUUID()
    private val existingWorkspaceOne =
        Workspace.create(existingUserId, 0, "Existing One").expectSuccess()
            .installApp(existingAppId, existingAppVersionId).expectSuccess()
    private val existingWorkspaceOneAppInstallationId = existingWorkspaceOne.appInstallations.first().id
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
            every { it.delete(any()) }.answers { Maybe.Result(Unit) }
        }

        appQueryPersistence = mockk<AppQueryPersistencePort>().also {
            every { it.getOrError(any(), any()) }.answers { Maybe.Error(AppErrorCodes.APP_VERSION_NOT_FOUND, null) }
            every { it.getOrError(existingAppId, existingAppVersionId) }.returns(Maybe.Result(existingAppVersion))
        }

        port = WorkspaceCommandAdapter(appQueryPersistence, queryPersistence, commandPersistence)
    }

    @Test
    fun `new valid workspace`() {
        fun Workspace.assertions() {
            assertThat(id).isNotIn(existingOneId, existingTwoId, unknownId)
            assertThat(userId).isEqualTo(existingUserId)
            assertThat(order).isEqualTo(2)
            assertThat(name).isEqualTo("A New Workspace")
            assertThat(appInstallations).isEmpty()
        }

        port.createWorkspace(existingUserId, "  A New Workspace   \t").expectSuccess().assertions()
        verifyMocks {
            queryPersistence.findForUser(existingUserId)
            commandPersistence.upsert(withArg {
                (this.actual as Workspace).assertions()
            })
        }
    }

    @Test
    fun `first valid workspace for user`() {
        fun Workspace.assertions() {
            assertThat(order).isEqualTo(0)
        }

        val userId = UUID.randomUUID()
        port.createWorkspace(userId, "My First Workspace").expectSuccess().assertions()
        verifyMocks {
            queryPersistence.findForUser(userId)
            commandPersistence.upsert(withArg {
                (actual as Workspace).assertions()
            })
        }
    }

    @Test
    fun `new workspace with blank name`() {
        port.createWorkspace(existingUserId, " ").expectError(
            code = WorkspaceErrorCodes.NAME_BLANK,
            details = null,
        )
        verifyMocks {
            queryPersistence.findForUser(existingUserId)
        }
    }

    @Test
    fun `reorder user workspaces`() {
        port.reorderWorkspaces(existingUserId, listOf(existingTwoId, existingOneId)).expectSuccess()
        verifyMocks {
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
        verifyMocks {
            queryPersistence.findForUser(existingUserId)
        }
    }

    @Test
    fun `reorder user workspaces with missing id`() {
        port.reorderWorkspaces(existingUserId, listOf(existingTwoId)).expectError(
            code = WorkspaceErrorCodes.REORDER_WORKSPACES_MISSING_IDS,
            details = listOf(existingOneId).toString(),
        )
        verifyMocks {
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
        verifyMocks {
            queryPersistence.findForUser(existingUserId)
        }
    }

    @Test
    fun `rename workspace`() {
        fun Workspace.assertions() {
            assertThat(name).isEqualTo("A New Name")
        }

        port.renameWorkspace(existingOneId, "A New Name").expectSuccess().assertions()
        verifyMocks {
            queryPersistence.getOrError(existingOneId)
            commandPersistence.upsert(withArg {
                (actual as Workspace).assertions()
            })
        }
    }

    @Test
    fun `rename workspace to blank name`() {
        port.renameWorkspace(existingOneId, " ").expectError(
            code = WorkspaceErrorCodes.NAME_BLANK,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(existingOneId)
        }
    }

    @Test
    fun `install unknown app version`() {
        port.installApp(existingOneId, existingAppId, unknownAppVersionId).expectError(
            code = AppErrorCodes.APP_VERSION_NOT_FOUND,
            details = null,
        )
        verifyMocks {
            appQueryPersistence.getOrError(existingAppId, unknownAppVersionId)
        }
    }

    @Test
    fun `install app`() {
        fun AppInstallation.assertions() {
            assertThat(appId).isEqualTo(existingAppId)
            assertThat(version).isEqualTo(existingAppVersionId)
            assertThat(nameSupplement).isNull()
        }

        port.installApp(existingTwoId, existingAppId, existingAppVersionId).expectSuccess().assertions()
        verifyMocks {
            appQueryPersistence.getOrError(existingAppId, existingAppVersionId)
            queryPersistence.getOrError(existingTwoId)
            commandPersistence.upsert(withArg {
                val workspace = actual as Workspace
                assertThat(workspace.id).isEqualTo(existingTwoId)
                assertThat(workspace.appInstallations).hasSize(1)
                workspace.appInstallations.first().assertions()
            })
        }
    }

    @Test
    fun `move app unknown source`() {
        port.moveApp(unknownId, existingWorkspaceOneAppInstallationId, existingTwoId).expectError(
            code = WorkspaceErrorCodes.NOT_FOUND,
            details = unknownId.toString(),
        )

        verifyMocks {
            queryPersistence.getOrError(unknownId)
        }
    }

    @Test
    fun `move app unknown app installation`() {
        port.moveApp(existingOneId, unknownId, existingTwoId).expectError(
            code = WorkspaceErrorCodes.APP_INSTALLATION_NOT_FOUND,
            details = unknownId.toString(),
        )

        verifyMocks {
            queryPersistence.getOrError(existingOneId)
        }
    }

    @Test
    fun `move app unknown target`() {
        port.moveApp(existingOneId, existingWorkspaceOneAppInstallationId, unknownId).expectError(
            code = WorkspaceErrorCodes.NOT_FOUND,
            details = unknownId.toString(),
        )

        verifyMocks {
            queryPersistence.getOrError(existingOneId)
            queryPersistence.getOrError(unknownId)
        }
    }

    @Test
    fun `move app`() {
        fun Workspace.assertionsSource() {
            assertThat(id).isEqualTo(existingOneId)
            assertThat(appInstallations).isEmpty()
        }

        fun Workspace.assertionsTarget() {
            assertThat(id).isEqualTo(existingTwoId)
            assertThat(appInstallations).hasSize(1)
            assertThat(appInstallations.first().id).isEqualTo(existingWorkspaceOneAppInstallationId)
        }

        port.moveApp(existingOneId, existingWorkspaceOneAppInstallationId, existingTwoId).expectSuccess().also {
            it.first.assertionsSource()
            it.second.assertionsTarget()
        }

        verifyMocks {
            queryPersistence.getOrError(existingOneId)
            queryPersistence.getOrError(existingTwoId)
            commandPersistence.upsert(withArg {
                (actual as Workspace).assertionsTarget()
            })
            commandPersistence.upsert(withArg {
                (actual as Workspace).assertionsSource()
            })
        }
    }

    // TODO #25 migrate
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

    // TODO #25 migrate
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

    // TODO #25 migrate
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

    // TODO #25 migrate
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
        fun AppInstallation.assertions() {
            assertThat(id).isEqualTo(existingWorkspaceOneAppInstallationId)
            assertThat(nameSupplement).isEqualTo("FOOOO")
        }

        port.nameApp(existingOneId, existingWorkspaceOneAppInstallationId, "FOOOO").expectSuccess().assertions()
        verifyMocks {
            queryPersistence.getOrError(existingOneId)
            commandPersistence.upsert(withArg {
                val workspace = actual as Workspace
                assertThat(workspace.id).isEqualTo(existingOneId)
                assertThat(workspace.appInstallations).hasSize(1)
                workspace.appInstallations.first().assertions()
            })
        }
    }

    @Test
    fun `name app installation unknown workspace`() {
        port.nameApp(unknownId, existingWorkspaceOneAppInstallationId, "FOOOO").expectError(
            code = WorkspaceErrorCodes.NOT_FOUND,
            details = unknownId.toString(),
        )
        verifyMocks {
            queryPersistence.getOrError(unknownId)
        }
    }

    @Test
    fun `name app installation unknown app`() {
        port.nameApp(existingOneId, unknownId, "FOOOO").expectError(
            code = WorkspaceErrorCodes.APP_INSTALLATION_NOT_FOUND,
            details = unknownId.toString()
        )

        verifyMocks {
            queryPersistence.getOrError(existingOneId)
        }
    }

    // TODO #25 migrate
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

    // TODO #25 migrate
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

    // TODO #25 migrate
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
        port.deleteWorkspace(existingTwoId).expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(existingTwoId)
            commandPersistence.delete(existingTwoId)
        }
    }

    @Test
    fun `delete not empty workspace`() {
        port.deleteWorkspace(existingOneId).expectError(
            code = WorkspaceErrorCodes.DELETE_WORKSPACE_INSTALLED_APPS,
            details = "1",
        )
    }

    @Test
    fun `delete unknown workspace`() {
        port.deleteWorkspace(unknownId).expectError(
            code = WorkspaceErrorCodes.NOT_FOUND,
            details = unknownId.toString(),
        )
    }

    private fun createWorkspace() = UUID.randomUUID().let {
        Workspace.create(it, 0, it.toString()).expectSuccess()
    }

    private fun verifyMocks(verifyBlock: (MockKVerificationScope.() -> Unit)? = null) {
        if (verifyBlock != null) {
            verifySequence(inverse = false, verifyBlock = verifyBlock)
        }
        confirmVerified(appQueryPersistence)
        confirmVerified(queryPersistence)
        confirmVerified(commandPersistence)
    }
}
