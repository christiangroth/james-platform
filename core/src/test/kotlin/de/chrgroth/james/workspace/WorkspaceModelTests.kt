package de.chrgroth.james.workspace

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.expectError
import de.chrgroth.james.expectSuccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

// TODO #25 switch to testing of Command

class WorkspaceModelTests {

    @Test
    fun `new valid workspace`() {
        Workspace.create(UUID.randomUUID(), 0, "Name").expectSuccess()
    }

    @Test
    fun `new workspace with negative order`() {
        Workspace.create(UUID.randomUUID(), -13, " ").expectError(
            code = WorkspaceErrorCodes.ORDER_NEGATIVE,
            details = "-13",
        )
    }

    @Test
    fun `new workspace with blank name`() {
        Workspace.create(UUID.randomUUID(), 0, " ").expectError(
            code = WorkspaceErrorCodes.NAME_BLANK,
            details = null,
        )
    }

    @Test
    fun `reorder workspace`() {
        val workspace = createWorkspace().changeOrder(13).expectSuccess()
        assertThat(workspace.order).isEqualTo(13)
    }

    @Test
    fun `reorder workspace with negative value`() {
        createWorkspace().changeOrder(-13).expectError(
            code = WorkspaceErrorCodes.ORDER_NEGATIVE,
            details = "-13",
        )
    }

    @Test
    fun `rename workspace`() {
        val workspace = createWorkspace().changeName("New Workspace").expectSuccess()
        assertThat(workspace.name).isEqualTo("New Workspace")
    }

    @Test
    fun `rename workspace to blank name`() {
        createWorkspace().changeName(" ").expectError(
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
        val workspace = createWorkspace().acceptAppMigration(AppInstallation(
            id = UUID.randomUUID(),
            appId = appId,
            version = Semver("1.0.0"),
            nameSupplement = null,
        )).expectSuccess()
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
            code = WorkspaceErrorCodes.APP_NOT_FOUND,
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
}

private fun createWorkspace() = UUID.randomUUID().let {
    Workspace.create(it, 0, it.toString()).expectSuccess()
}
