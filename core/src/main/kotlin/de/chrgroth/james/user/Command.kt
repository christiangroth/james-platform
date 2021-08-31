package de.chrgroth.james.user

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import java.util.UUID

// TODO #22 need to check if user is active

// TODO #3 split user and workspace??
interface UserCommandPort {
    // TODO #3 validate email pattern(".+@.+\..+")
    fun registerUser(email: String, name: String): Maybe<User>
    fun deleteUser(id: UUID): Maybe<Unit>

    fun createWorkspace(userId: UUID, name: String): Maybe<UserWorkspace>
    fun renameWorkspace(userId: UUID, id: UUID, newName: String): Maybe<UserWorkspace>
    fun deleteWorkspace(userId: UUID, id: UUID): Maybe<Unit>

    fun installApp(workspaceId: UUID, appId: UUID, appVersion: Semver): Maybe<AppInstallation>
    fun nameAppInstallation(workspaceId: UUID, appId: UUID, appVersion: Semver, nameSupplement: String?)
    fun categorizeAppInstallation(workspaceId: UUID, appId: UUID, appVersion: Semver, category: String?)
    fun tagAppInstallation(workspaceId: UUID, appId: UUID, appVersion: Semver, tags: Set<String>?)
    fun moveAppInstallation(workspaceId: UUID, appId: UUID, appVersion: Semver, newWorkspaceId: UUID)
    fun uninstallApp(workspaceId: UUID, appId: UUID, appVersion: Semver): Maybe<Unit>
}

// TODO #3 implement and test
internal class UserCommandAdapter(
    private val userQueryPersistence: UserQueryPersistencePort,
    private val userWorkspaceQueryPersistence: UserWorkspaceQueryPersistencePort,
    private val userCommandPersistence: UserCommandPersistencePort,
    private val userWorkspaceCommandPersistence: UserWorkspaceCommandPersistencePort,
) : UserCommandPort {

    override fun registerUser(email: String, name: String): Maybe<User> {
        val userByEmailResult = userQueryPersistence.getByEmail(email)
        val userExists = userByEmailResult is Result && userByEmailResult.value != null
        if (userExists) {
            return Error(
                code = UserErrorCodes.REGISTRATION_EMAIL_EXISTS,
                details = null,
            )
        }

        return User.validateEmail(email).transform {
            userCommandPersistence.upsert(
                User(
                    id = UUID.randomUUID(),
                    email = email,
                    name = name,
                    workspaces = emptySet(),
                )
            )
        }
    }

    override fun deleteUser(id: UUID) =
        id.loadUserAndInvoke(User::canBeDeleted) { _, _ ->
            userCommandPersistence.delete(id)
        }

    override fun createWorkspace(userId: UUID, name: String) =
        userId.loadUserAndInvoke({ it.createWorkspace(name) }) { user, _ ->
            userCommandPersistence.upsert(user).map { persistentUser ->
                persistentUser.workspaces.first { it.name == name }
            }
        }

    override fun renameWorkspace(userId: UUID, id: UUID, newName: String) =
        userId.loadUserAndInvoke({ it.renameWorkspace(id, newName) }) { user, _ ->
            userCommandPersistence.upsert(user).map { persistentUser ->
                persistentUser.workspaces.first { it.name == newName }
            }
        }

    override fun deleteWorkspace(userId: UUID, id: UUID) =
        userId.loadUserAndInvoke({ it.deleteWorkspace(id) }) { user, _ ->
            userCommandPersistence.upsert(user).map { }
        }

    private fun <R, S> UUID.loadUserAndInvoke(
        userOperation: (User) -> Maybe<R>,
        persistenceOperation: (User, R) -> Maybe<S>,
    ) =
        userQueryPersistence.get(this).transform { user ->
            if (user == null) {
                Error(
                    code = UserErrorCodes.NOT_FOUND,
                    details = null,
                )
            } else {
                userOperation(user).transform { persistenceOperation(user, it) }
            }
        }

    override fun installApp(workspaceId: UUID, appId: UUID, appVersion: Semver): Maybe<AppInstallation> {
        TODO("Not yet implemented")
    }

    override fun nameAppInstallation(workspaceId: UUID, appId: UUID, appVersion: Semver, nameSupplement: String?) {
        TODO("Not yet implemented")
    }

    override fun categorizeAppInstallation(workspaceId: UUID, appId: UUID, appVersion: Semver, category: String?) {
        TODO("Not yet implemented")
    }

    override fun tagAppInstallation(workspaceId: UUID, appId: UUID, appVersion: Semver, tags: Set<String>?) {
        TODO("Not yet implemented")
    }

    override fun moveAppInstallation(workspaceId: UUID, appId: UUID, appVersion: Semver, newWorkspaceId: UUID) {
        TODO("Not yet implemented")
    }

    override fun uninstallApp(workspaceId: UUID, appId: UUID, appVersion: Semver): Maybe<Unit> {
        TODO("Not yet implemented")
    }

    private fun <R, S> Pair<UUID, UUID>.loadWorkspaceAndInvoke(
        workspaceOperation: (UserWorkspace) -> Maybe<R>,
        persistenceOperation: (UserWorkspace, R) -> Maybe<S>,
    ) =
        userWorkspaceQueryPersistence.get(this.first, this.second).transform { workspace ->
            if (workspace == null) {
                Error(
                    code = WorkspaceErrorCodes.NOT_FOUND,
                    details = null,
                )
            } else {
                workspaceOperation(workspace).transform { persistenceOperation(workspace, it) }
            }
        }
}
