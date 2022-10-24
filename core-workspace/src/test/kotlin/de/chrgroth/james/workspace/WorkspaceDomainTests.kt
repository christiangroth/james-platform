package de.chrgroth.james.workspace

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Error
import de.chrgroth.james.Maybe
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

class WorkspaceDomainTests {

    private val existingAppIdOne = UUID.randomUUID()
    private val existingAppIdTwo = UUID.randomUUID()
    private val existingAppOneVersionIdZero = Semver("0.9.4")
    private val existingAppOneVersionIdOne = Semver("1.0.0")
    private val existingAppOneVersionIdTwo = Semver("1.1.0")
    private val existingAppTwoVersionIdOne = Semver("0.1.0")
    private val unknownAppVersionId = Semver("1.2.3")
    private val existingAppVersion = AppVersion.create(
        releaseNotes = AppVersionReleaseNotes.create(AppVersionChangeType.FEATURE, "First Release").expectSuccess(),
        nextVersionDraft = AppVersionDraft.create().expectSuccess(),
        latestVersion = null,
    ).expectSuccess()
    private val existingAppNewerVersion = AppVersion.create(
        releaseNotes = AppVersionReleaseNotes.create(AppVersionChangeType.FEATURE, "Second Release").expectSuccess(),
        nextVersionDraft = AppVersionDraft.create().expectSuccess(),
        latestVersion = existingAppVersion,
    ).expectSuccess()

    private val existingUserId = UUID.randomUUID()
    private val existingWorkspaceOne =
        Workspace.create(userId = existingUserId, order = 0, name = "Existing One").expectSuccess()
            .installApp(existingAppIdOne, existingAppOneVersionIdOne).expectSuccess()
            .installApp(existingAppIdTwo, existingAppTwoVersionIdOne).expectSuccess()
    private val existingWorkspaceOneAppInstallationOneId = existingWorkspaceOne.appInstallations.first().id
    private val existingWorkspaceOneAppInstallationTwoId = existingWorkspaceOne.appInstallations.last().id
    private val existingWorkspaceTwo = Workspace.create(userId = existingUserId, order = 1, name = "Existing Two").expectSuccess()
    private val existingOneId = existingWorkspaceOne.id
    private val existingTwoId = existingWorkspaceTwo.id
    private val unknownId = UUID.randomUUID()

    private lateinit var appQueryPersistence: AppQueryPersistencePort
    private lateinit var queryPersistence: WorkspaceQueryPersistencePort
    private lateinit var commandPersistence: WorkspaceCommandPersistencePort
    private lateinit var workspaceUseCases: WorkspaceUseCases
    private lateinit var appInstallationUseCases: AppInstallationUseCases

    @BeforeEach
    internal fun initialize() {
        queryPersistence = mockk<WorkspaceQueryPersistencePort>().also {
            every { it.getOrError(existingOneId) } returns (Maybe.Result(existingWorkspaceOne))
            every { it.getOrError(existingTwoId) } returns (Maybe.Result(existingWorkspaceTwo))
            every { it.getOrError(unknownId) } returns (Error(WorkspaceErrorCodes.NOT_FOUND, unknownId.toString()))

            every { it.findForUser(any()) } returns (Maybe.Result(listOf()))
            every { it.findForUser(existingWorkspaceOne.userId) } returns (Maybe.Result(listOf(existingWorkspaceOne, existingWorkspaceTwo)))
        }

        commandPersistence = mockk<WorkspaceCommandPersistencePort>().also {
            every { it.upsert(any()) } answers { Maybe.Result(this.args[0] as Workspace) }
            every { it.delete(any()) } answers { Maybe.Result(Unit) }
        }

        appQueryPersistence = mockk<AppQueryPersistencePort>().also {
            every { it.getOrError(any(), any()) } answers { Error(AppErrorCodes.RELEASE_VERSION_NOT_FOUND, null) }
            every { it.getOrError(existingAppIdOne, existingAppOneVersionIdZero) } returns (Maybe.Result(mockk()))
            every { it.getOrError(existingAppIdOne, existingAppOneVersionIdOne) } returns (Maybe.Result(existingAppVersion))
            every { it.getOrError(existingAppIdOne, existingAppOneVersionIdTwo) } returns (Maybe.Result(existingAppNewerVersion))
        }

        workspaceUseCases = WorkspaceUseCasesService(queryPersistence, commandPersistence)
        appInstallationUseCases = AppInstallationUseCasesService(appQueryPersistence, queryPersistence, commandPersistence)
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

        workspaceUseCases.createWorkspace(existingUserId, "  A New Workspace   \t").expectSuccess().assertions()
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
        workspaceUseCases.createWorkspace(userId, "My First Workspace").expectSuccess().assertions()
        verifyMocks {
            queryPersistence.findForUser(userId)
            commandPersistence.upsert(withArg {
                (actual as Workspace).assertions()
            })
        }
    }

    @Test
    fun `new workspace with blank name`() {
        workspaceUseCases.createWorkspace(existingUserId, " ").expectError(
            code = WorkspaceErrorCodes.NAME_BLANK,
            details = null,
        )
        verifyMocks {
            queryPersistence.findForUser(existingUserId)
        }
    }

    @Test
    fun `new workspace with negative order`() {
        Workspace.create(userId = existingUserId, order = -1, name = "Existing One").expectError(
            code = WorkspaceErrorCodes.ORDER_NEGATIVE,
            details = "-1",
        )
    }

    @Test
    fun `reorder user workspaces`() {
        workspaceUseCases.reorderWorkspaces(existingUserId, listOf(existingTwoId, existingOneId)).expectSuccess()
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
        workspaceUseCases.reorderWorkspaces(existingUserId, listOf(existingTwoId, existingOneId, unknownId)).expectError(
            code = WorkspaceErrorCodes.REORDER_WORKSPACES_UNKNOWN_IDS,
            details = listOf(unknownId).toString(),
        )
        verifyMocks {
            queryPersistence.findForUser(existingUserId)
        }
    }

    @Test
    fun `reorder user workspaces with missing id`() {
        workspaceUseCases.reorderWorkspaces(existingUserId, listOf(existingTwoId)).expectError(
            code = WorkspaceErrorCodes.REORDER_WORKSPACES_MISSING_IDS,
            details = listOf(existingOneId).toString(),
        )
        verifyMocks {
            queryPersistence.findForUser(existingUserId)
        }
    }

    @Test
    fun `reorder user workspaces with missing and unknown id`() {
        workspaceUseCases.reorderWorkspaces(existingUserId, listOf(unknownId)).expectErrors(
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

        workspaceUseCases.renameWorkspace(existingOneId, "A New Name").expectSuccess().assertions()
        verifyMocks {
            queryPersistence.getOrError(existingOneId)
            commandPersistence.upsert(withArg {
                (actual as Workspace).assertions()
            })
        }
    }

    @Test
    fun `rename workspace to blank name`() {
        workspaceUseCases.renameWorkspace(existingOneId, " ").expectError(
            code = WorkspaceErrorCodes.NAME_BLANK,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(existingOneId)
        }
    }

    @Test
    fun `install unknown app version`() {
        appInstallationUseCases.installApp(existingOneId, existingAppIdOne, unknownAppVersionId).expectError(
            code = AppErrorCodes.RELEASE_VERSION_NOT_FOUND,
            details = null,
        )
        verifyMocks {
            appQueryPersistence.getOrError(existingAppIdOne, unknownAppVersionId)
        }
    }

    @Test
    fun `install app`() {
        fun AppInstallation.assertions() {
            assertThat(appId).isEqualTo(existingAppIdOne)
            assertThat(version).isEqualTo(existingAppOneVersionIdOne)
            assertThat(nameSupplement).isNull()
        }

        appInstallationUseCases.installApp(existingTwoId, existingAppIdOne, existingAppOneVersionIdOne).expectSuccess().assertions()
        verifyMocks {
            appQueryPersistence.getOrError(existingAppIdOne, existingAppOneVersionIdOne)
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
        appInstallationUseCases.moveApp(unknownId, existingWorkspaceOneAppInstallationOneId, existingTwoId).expectError(
            code = WorkspaceErrorCodes.NOT_FOUND,
            details = unknownId.toString(),
        )

        verifyMocks {
            queryPersistence.getOrError(unknownId)
        }
    }

    @Test
    fun `move app unknown app installation`() {
        appInstallationUseCases.moveApp(existingOneId, unknownId, existingTwoId).expectError(
            code = WorkspaceErrorCodes.INSTALLATION_NOT_FOUND,
            details = unknownId.toString(),
        )

        verifyMocks {
            queryPersistence.getOrError(existingOneId)
        }
    }

    @Test
    fun `move app unknown target`() {
        appInstallationUseCases.moveApp(existingOneId, existingWorkspaceOneAppInstallationOneId, unknownId).expectError(
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
            assertThat(appInstallations).hasSize(1)
            assertThat(appInstallations.first().id).isEqualTo(existingWorkspaceOneAppInstallationTwoId)
        }

        fun Workspace.assertionsTarget() {
            assertThat(id).isEqualTo(existingTwoId)
            assertThat(appInstallations).hasSize(1)
            assertThat(appInstallations.first().id).isEqualTo(existingWorkspaceOneAppInstallationOneId)
        }

        appInstallationUseCases.moveApp(existingOneId, existingWorkspaceOneAppInstallationOneId, existingTwoId).expectSuccess().also {
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

    @Test
    fun `reorder apps`() {
        fun Workspace.assertions() {
            assertThat(appInstallations).hasSize(2)
            assertThat(appInstallations.first().id).isEqualTo(existingWorkspaceOneAppInstallationTwoId)
            assertThat(appInstallations.last().id).isEqualTo(existingWorkspaceOneAppInstallationOneId)
        }

        val newOrder = listOf(existingWorkspaceOneAppInstallationTwoId, existingWorkspaceOneAppInstallationOneId)
        appInstallationUseCases.reorderApps(existingOneId, newOrder).expectSuccess().assertions()

        verifyMocks {
            queryPersistence.getOrError(existingOneId)
            commandPersistence.upsert(withArg {
                val workspace = actual as Workspace
                assertThat(workspace.id).isEqualTo(existingOneId)
                workspace.assertions()
            })
        }
    }

    @Test
    fun `reorder apps with unknown and missing ids`() {
        val newOrder = listOf(existingWorkspaceOneAppInstallationTwoId, unknownId)
        appInstallationUseCases.reorderApps(existingOneId, newOrder).expectErrors(
            Error(
                code = WorkspaceErrorCodes.REORDER_APPS_UNKNOWN_IDS,
                details = listOf(unknownId).toString(),
            ),
            Error(
                code = WorkspaceErrorCodes.REORDER_APPS_MISSING_IDS,
                details = listOf(existingWorkspaceOneAppInstallationOneId).toString(),
            ),
        )

        verifyMocks {
            queryPersistence.getOrError(existingOneId)
        }
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
        fun AppInstallation.assertions() {
            assertThat(id).isEqualTo(existingWorkspaceOneAppInstallationOneId)
            assertThat(nameSupplement).isEqualTo("FOOOO")
        }

        appInstallationUseCases.nameApp(existingOneId, existingWorkspaceOneAppInstallationOneId, "FOOOO").expectSuccess().assertions()
        verifyMocks {
            queryPersistence.getOrError(existingOneId)
            commandPersistence.upsert(withArg {
                val workspace = actual as Workspace
                assertThat(workspace.id).isEqualTo(existingOneId)
                workspace.appInstallations.first { it.id == existingWorkspaceOneAppInstallationOneId }.assertions()
            })
        }
    }

    @Test
    fun `name app installation unknown workspace`() {
        appInstallationUseCases.nameApp(unknownId, existingWorkspaceOneAppInstallationOneId, "FOOOO").expectError(
            code = WorkspaceErrorCodes.NOT_FOUND,
            details = unknownId.toString(),
        )
        verifyMocks {
            queryPersistence.getOrError(unknownId)
        }
    }

    @Test
    fun `name app installation unknown app`() {
        appInstallationUseCases.nameApp(existingOneId, unknownId, "FOOOO").expectError(
            code = WorkspaceErrorCodes.INSTALLATION_NOT_FOUND,
            details = unknownId.toString()
        )

        verifyMocks {
            queryPersistence.getOrError(existingOneId)
        }
    }

    @Test
    fun `update app installation`() {
        fun AppInstallation.assertions() {
            assertThat(appId).isEqualTo(existingAppIdOne)
            assertThat(version).isEqualTo(existingAppOneVersionIdTwo)
        }

        appInstallationUseCases.updateApp(existingOneId, existingWorkspaceOneAppInstallationOneId, existingAppOneVersionIdTwo).expectSuccess().assertions()
        verifyMocks {
            queryPersistence.getOrError(existingOneId)
            appQueryPersistence.getOrError(existingAppIdOne, existingAppOneVersionIdTwo)
            commandPersistence.upsert(withArg {
                val workspace = actual as Workspace
                val appInstallation = workspace.appInstallations.firstOrNull {
                    it.appId == existingAppIdOne && it.version == existingAppOneVersionIdTwo
                }
                assertThat(appInstallation).isNotNull
                appInstallation!!.assertions()
            })
        }
    }

    @Test
    fun `update app installation unknown workspace`() {
        appInstallationUseCases.updateApp(unknownId, existingWorkspaceOneAppInstallationOneId, existingAppOneVersionIdTwo).expectError(
            code = WorkspaceErrorCodes.NOT_FOUND,
            details = unknownId.toString(),
        )

        verifyMocks {
            queryPersistence.getOrError(unknownId)
        }
    }

    @Test
    fun `update app installation unknown app installation`() {
        appInstallationUseCases.updateApp(existingOneId, unknownId, existingAppOneVersionIdTwo).expectError(
            code = WorkspaceErrorCodes.INSTALLATION_NOT_FOUND,
            details = unknownId.toString(),
        )

        verifyMocks {
            queryPersistence.getOrError(existingOneId)
        }
    }

    @Test
    fun `update app installation unknown app version`() {
        appInstallationUseCases.updateApp(existingOneId, existingWorkspaceOneAppInstallationOneId, unknownAppVersionId).expectError(
            code = AppErrorCodes.RELEASE_VERSION_NOT_FOUND,
            details = null,
        )

        verifyMocks {
            queryPersistence.getOrError(existingOneId)
            appQueryPersistence.getOrError(existingAppIdOne, unknownAppVersionId)
        }
    }

    @Test
    fun `update app installation same app version`() {
        appInstallationUseCases.updateApp(existingOneId, existingWorkspaceOneAppInstallationOneId, existingAppOneVersionIdOne).expectError(
            code = WorkspaceErrorCodes.DOWNGRADE_NOT_SUPPORTED,
            details = "1.0.0 >= 1.0.0",
        )

        verifyMocks {
            queryPersistence.getOrError(existingOneId)
            appQueryPersistence.getOrError(existingAppIdOne, existingAppOneVersionIdOne)
        }
    }

    @Test
    fun `update app installation older app version`() {
        appInstallationUseCases.updateApp(existingOneId, existingWorkspaceOneAppInstallationOneId, existingAppOneVersionIdZero).expectError(
            code = WorkspaceErrorCodes.DOWNGRADE_NOT_SUPPORTED,
            details = "1.0.0 >= 0.9.4",
        )

        verifyMocks {
            queryPersistence.getOrError(existingOneId)
            appQueryPersistence.getOrError(existingAppIdOne, existingAppOneVersionIdZero)
        }
    }

    @Test
    fun `uninstall app`() {
        appInstallationUseCases.uninstallApp(existingOneId, existingWorkspaceOneAppInstallationOneId).expectError(
            code = WorkspaceErrorCodes.UNINSTALL_NOT_SUPPORTED,
            details = null,
        )

        verifyMocks {
            queryPersistence.getOrError(existingOneId)
        }
    }

    @Test
    fun `uninstall app unknown workspace`() {
        appInstallationUseCases.uninstallApp(unknownId, existingWorkspaceOneAppInstallationOneId).expectError(
            code = WorkspaceErrorCodes.NOT_FOUND,
            details = unknownId.toString(),
        )

        verifyMocks {
            queryPersistence.getOrError(unknownId)
        }
    }

    @Test
    fun `uninstall app unknown app installation`() {
        appInstallationUseCases.uninstallApp(existingOneId, unknownId).expectError(
            code = WorkspaceErrorCodes.INSTALLATION_NOT_FOUND,
            details = unknownId.toString(),
        )

        verifyMocks {
            queryPersistence.getOrError(existingOneId)
        }
    }

    @Test
    fun `delete empty workspace`() {
        workspaceUseCases.deleteWorkspace(existingTwoId).expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(existingTwoId)
            commandPersistence.delete(existingTwoId)
        }
    }

    @Test
    fun `delete not empty workspace`() {
        workspaceUseCases.deleteWorkspace(existingOneId).expectError(
            code = WorkspaceErrorCodes.DELETE_WORKSPACE_INSTALLED_APPS,
            details = "2",
        )
    }

    @Test
    fun `delete unknown workspace`() {
        workspaceUseCases.deleteWorkspace(unknownId).expectError(
            code = WorkspaceErrorCodes.NOT_FOUND,
            details = unknownId.toString(),
        )
    }

    private fun createWorkspace() = UUID.randomUUID().let {
        Workspace.create(userId = it, order = 0, name = it.toString()).expectSuccess()
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
