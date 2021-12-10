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
        Workspace.create(UUID.randomUUID(), "Name").expectSuccess()
    }

    @Test
    fun `new workspace with blank name`() {
        Workspace.create(UUID.randomUUID(), " ").expectError(
            code = WorkspaceErrorCodes.CREATE_WORKSPACE_NAME_BLANK,
            details = null,
        )
    }

    @Test
    fun `rename workspace`() {
        val workspace = createWorkspace().rename("New Workspace").expectSuccess()
        assertThat(workspace.name).isEqualTo("New Workspace")
    }

    @Test
    fun `rename workspace to blank name`() {
        createWorkspace().rename(" ").expectError(
            code = WorkspaceErrorCodes.RENAME_WORKSPACE_NAME_BLANK,
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
        createWorkspace().canBeDeleted().expectSuccess()
    }

    @Test
    fun `delete not empty workspace`() {
        createWorkspace()
            .installApp(UUID.randomUUID(), Semver("1.0.0")).expectSuccess()
            .canBeDeleted().expectError(
                code = WorkspaceErrorCodes.DELETE_WORKSPACE_INSTALLED_APPS,
                details = "1",
            )
    }
}

private fun createWorkspace() = UUID.randomUUID().let {
    Workspace.create(it, it.toString()).expectSuccess()
}
