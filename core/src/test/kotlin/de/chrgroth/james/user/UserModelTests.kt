package de.chrgroth.james.user

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.expectError
import de.chrgroth.james.expectSuccess
import org.assertj.core.api.Assertions
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
        Assertions.assertThat(user.workspaces).hasSize(1)
        Assertions.assertThat(user.workspaces.first().name).isEqualTo("Apps")
        Assertions.assertThat(user.workspaces.first().apps).isEqualTo(emptySet<AppInstallation>())
    }

    @Test
    fun `two workspaces with same name`() {
        val user = createUser()
            .createWorkspace("Apps").expectSuccess()
            .createWorkspace("Apps").expectSuccess()
        Assertions.assertThat(user.workspaces).hasSize(2)
        val firstWorksapce = user.workspaces.toList()[0]
        val secondWorksapce = user.workspaces.toList()[1]
        Assertions.assertThat(firstWorksapce.name).isEqualTo("Apps")
        Assertions.assertThat(firstWorksapce.apps).isEqualTo(emptySet<AppInstallation>())
        Assertions.assertThat(secondWorksapce.name).isEqualTo("Apps")
        Assertions.assertThat(secondWorksapce.apps).isEqualTo(emptySet<AppInstallation>())
        Assertions.assertThat(firstWorksapce.id).isNotEqualTo(secondWorksapce.id)
    }

    @Test
    fun `renaming workspace`() {
        val user = createUser().createWorkspace("Apps").expectSuccess()
        val updatedUser = user.renameWorkspace(user.workspaces.first().id, "New Apps").expectSuccess()
        Assertions.assertThat(updatedUser.workspaces).hasSize(1)
        Assertions.assertThat(updatedUser.workspaces.first().name).isEqualTo("New Apps")
        Assertions.assertThat(updatedUser.workspaces.first().apps).isEqualTo(emptySet<AppInstallation>())
    }

    // TODO #3 test move app installation

    @Test
    fun `delete unknown workspace`() {
        val user = createUser().createWorkspace("Apps").expectSuccess()
        UUID.randomUUID().also {
            user.deleteWorkspace(it).expectError(
                code = WorkspaceErrorCodes.NOT_FOUND,
                details = "Workspace with id $it not found",
            )
        }
        Assertions.assertThat(user.workspaces).hasSize(1)
    }

    @Test
    fun `delete empty workspace`() {
        val user = createUser().createWorkspace("Apps").expectSuccess()
        val updatedUser = user.deleteWorkspace(user.workspaces.first().id).expectSuccess()
        Assertions.assertThat(updatedUser.workspaces).isEmpty()
    }

    // TODO #3 delete not empty workspace

    @Test
    fun `user without workspaces can be deleted`() {
        createUser().canBeDeleted().expectSuccess()
    }

    @Test
    fun `user with empty workspaces only can be deleted`() {
        createUser().createWorkspace("Apps").expectSuccess().canBeDeleted().expectSuccess()
    }

    // TODO #3 user with installed app can not be deleted

    private fun createUser() = UUID.randomUUID().let { id ->
        User(
            id = id,
            email = "${id}@gmail.com",
            name = id.toString(),
            workspaces = emptySet(),
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
            category = null,
            tags = emptySet(),
        )
        appInstallation.canBeDeleted().expectError(
            code = AppInstallationErrorCodes.DELETE_NOT_SUPPORTED,
            details = "Uninstalling apps it currently not supported",
        )
    }
}