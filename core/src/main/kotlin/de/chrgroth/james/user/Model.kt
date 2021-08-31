package de.chrgroth.james.user

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import java.util.UUID

private val simpleEmailPattern = Regex(".+@.+\\..+")

data class User(
    val id: UUID,
    val email: String,
    val name: String,
    val workspaces: Set<UserWorkspace> = emptySet(),
) {
    companion object {
        fun validateEmail(email: String): Maybe<String> {
            return if(email.matches(simpleEmailPattern)) {
                Maybe.Result(email)
            } else {
                Maybe.Error(
                    code = UserErrorCodes.REGISTRATION_EMAIL_INVALID,
                    details = "$email does not match $simpleEmailPattern",
                )
            }
        }
    }

    internal fun createWorkspace(name: String): Maybe<UserWorkspace> {
        TODO()
    }

    internal fun renameWorkspace(id: UUID, newName: String): Maybe<UserWorkspace> {
        TODO()
    }

    internal fun moveAppInstallation(workspaceId: UUID, appId: UUID, appVersion: Semver, newWorkspaceId: UUID) {
        TODO("Not yet implemented")
    }

    internal fun deleteWorkspace(id: UUID): Maybe<Unit> {
        TODO()
    }

    internal fun canBeDeleted() =
        when {
            else -> Maybe.Result(Unit)
        }
}

// TODO #3 model installations as back reference to workspace?
data class UserWorkspace(
    val id: UUID,
    val name: String,
    val apps: Set<AppInstallation>,
) {

    internal fun installApp(appId: UUID, appVersion: Semver): Maybe<AppInstallation> {
        TODO("Not yet implemented")
    }

    internal fun nameAppInstallation(appId: UUID, appVersion: Semver, nameSupplement: String?) {
        TODO("Not yet implemented")
    }

    internal fun categorizeAppInstallation(appId: UUID, appVersion: Semver, category: String?) {
        TODO("Not yet implemented")
    }

    internal fun tagAppInstallation(appId: UUID, appVersion: Semver, tags: Set<String>?) {
        TODO("Not yet implemented")
    }

    internal fun uninstallApp(appId: UUID, appVersion: Semver): Maybe<Unit> {
        TODO("Not yet implemented")
    }
}

// TODO #8 add data sharing options
// TODO #8 think about access for devices / api keys
data class AppInstallation(
    val id: UUID,
    val appId: UUID,
    val version: Semver,
    val nameSupplement: String? = null,
    val category: String? = null,
    val tags: Set<String>? = emptySet(),
)
