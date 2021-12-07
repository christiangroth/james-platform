package de.chrgroth.james.user

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.expectError
import de.chrgroth.james.expectSuccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class UserEmailValidationTests {

    @Test
    fun `valid email examples`() {
        User.validateEmail("someone@gmx.de").expectSuccess()
        User.validateEmail("someone@gmx.net").expectSuccess()
        User.validateEmail("some.one@gmail.com").expectSuccess()
    }

    @Test
    fun `invalid email examples`() {
        User.validateEmail("@gmx.de").expectError(
            code = UserErrorCodes.REGISTRATION_EMAIL_INVALID,
            details = "@gmx.de does not match .+@.+\\..+",
        )
        User.validateEmail("someone@gmx").expectError(
            code = UserErrorCodes.REGISTRATION_EMAIL_INVALID,
            details = "someone@gmx does not match .+@.+\\..+",
        )
    }
}

class UserModelTests {

    @Test
    fun `new valid workspace`() {
        val user = createUser().createWorkspace("Apps").expectSuccess()
        assertThat(user.workspaces).hasSize(1)
        assertThat(user.workspaces.first().name).isEqualTo("Apps")
        assertThat(user.workspaces.first().appInstallations).isEqualTo(emptyList<AppInstallation>())
    }

    @Test
    fun `two workspaces with same name`() {
        val user = createUser()
            .createWorkspace("Apps").expectSuccess()
            .createWorkspace("Apps").expectSuccess()
        assertThat(user.workspaces).hasSize(2)
        val firstWorksapce = user.workspaces.toList()[0]
        val secondWorksapce = user.workspaces.toList()[1]
        assertThat(firstWorksapce.name).isEqualTo("Apps")
        assertThat(firstWorksapce.appInstallations).isEqualTo(emptyList<AppInstallation>())
        assertThat(secondWorksapce.name).isEqualTo("Apps")
        assertThat(secondWorksapce.appInstallations).isEqualTo(emptyList<AppInstallation>())
        assertThat(firstWorksapce.id).isNotEqualTo(secondWorksapce.id)
    }

    @Test
    fun `renaming workspace`() {
        val user = createUser().createWorkspace("Apps").expectSuccess()
        val updatedUser = user.renameWorkspace(user.workspaces.first().id, "New Apps").expectSuccess()
        assertThat(updatedUser.workspaces).hasSize(1)
        assertThat(updatedUser.workspaces.first().name).isEqualTo("New Apps")
        assertThat(updatedUser.workspaces.first().appInstallations).isEqualTo(emptyList<AppInstallation>())
    }

    @Test
    fun `move app installation`() {
        val user = createUser()
            .createWorkspace("source").expectSuccess()
            .createWorkspace("target").expectSuccess()
        val sourceWorkspace = user.workspaces.first { it.name == "source" }

        /*val updatedUser = */sourceWorkspace.installApp(UUID.randomUUID(), Semver("1.0.0")).expectSuccess()
        // TODO #25 handling is shit, unable to test moving due to mismatching return type
        // val targetWorkspace = updatedUser.workspaces.first { it.name == "target" }.id
    }

    @Test
    fun `delete unknown workspace`() {
        val user = createUser().createWorkspace("Apps").expectSuccess()
        UUID.randomUUID().also {
            user.deleteWorkspace(it).expectError(
                code = WorkspaceErrorCodes.NOT_FOUND,
                details = "Workspace with id $it not found",
            )
        }
        assertThat(user.workspaces).hasSize(1)
    }

    @Test
    fun `delete empty workspace`() {
        val user = createUser().createWorkspace("Apps").expectSuccess()
        val updatedUser = user.deleteWorkspace(user.workspaces.first().id).expectSuccess()
        assertThat(updatedUser.workspaces).isEmpty()
    }

    // TODO #25 handling is shit ... updating workspace but checking canBeDeleted() on User
    @Test
    fun `delete not empty workspace`() {
        val user = createUser().createWorkspace("Apps").expectSuccess()
        user.workspaces.first().installApp(UUID.randomUUID(), Semver("1.0.0")).expectSuccess()
        val updatedUser = user.deleteWorkspace(user.workspaces.first().id).expectSuccess()
        assertThat(updatedUser.workspaces).isEmpty()
    }

    @Test
    fun `user without workspaces can be deleted`() {
        createUser().canBeDeleted().expectSuccess()
    }

    @Test
    fun `user with empty workspaces only can be deleted`() {
        createUser().createWorkspace("Apps").expectSuccess().canBeDeleted().expectSuccess()
    }

    // TODO #25 handling is shit ... updating workspace but checking canBeDeleted() on User
    @Test
    fun `user with installed app can't be deleted`() {
        val user = createUser().createWorkspace("Apps").expectSuccess()
        val updatedWorkspace = user.workspaces.first().installApp(UUID.randomUUID(), Semver("1.0.0")).expectSuccess()
        updatedWorkspace.canBeDeleted().expectError(
            code = WorkspaceErrorCodes.DELETE_INSTALLED_APPS,
            details = "Deletion not possible, there is still 1 app installation",
        )
    }

    // TODO #25 handling is shit ... updating workspace but checking canBeDeleted() on User
    @Test
    fun `user with multiple installed apps can't be deleted`() {
        val user = createUser().createWorkspace("Apps").expectSuccess()
        val updatedWorkspace = user.workspaces.first().installApp(UUID.randomUUID(), Semver("1.0.0")).expectSuccess()
        val finalWorkspace = updatedWorkspace.installApp(UUID.randomUUID(), Semver("1.0.0")).expectSuccess()
        finalWorkspace.canBeDeleted().expectError(
            code = WorkspaceErrorCodes.DELETE_INSTALLED_APPS,
            details = "Deletion not possible, there are still 2 app installations",
        )
    }
}

class UserWorkspaceModelTests {

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
            code = AppInstallationErrorCodes.DELETE_NOT_SUPPORTED,
            details = "Uninstalling apps is currently not supported",
        )
    }

    @Test
    fun `uninstall unknown app`() {
        val appId = UUID.randomUUID()
        val workspace = createWorkspace().installApp(appId, Semver("1.0.0")).expectSuccess()
        val unknownAppInstallationId = UUID.randomUUID()
        workspace.uninstallApp(unknownAppInstallationId).expectError(
            code = AppInstallationErrorCodes.NOT_FOUND,
            details = "App installation with id $unknownAppInstallationId not found",
        )
    }
}

class AppInstallationTests {

    @Test
    fun `uninstalling apps is not supported`() {
        val appInstallation = AppInstallation(
            id = UUID.randomUUID(),
            appId = UUID.randomUUID(),
            version = Semver("1.0.0"),
            nameSupplement = null,
        )
        appInstallation.canBeDeleted().expectError(
            code = AppInstallationErrorCodes.DELETE_NOT_SUPPORTED,
            details = "Uninstalling apps is currently not supported",
        )
    }
}

private fun createUser() = UUID.randomUUID().let { id ->
    User(
        id = id,
        email = "${id}@gmail.com",
        name = id.toString(),
        workspaces = emptyList(),
    )
}

private fun createWorkspace() = UUID.randomUUID().let { id ->
    UserWorkspace(
        id = id,
        name = id.toString(),
        appInstallations = emptyList(),
    )
}
