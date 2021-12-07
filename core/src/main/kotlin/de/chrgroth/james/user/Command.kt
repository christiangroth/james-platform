package de.chrgroth.james.user

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import java.util.UUID

// TODO #22 need to check if user is active

// TODO #25 restructure persistence!

interface UserCommandPort {
    fun registerUser(email: String, name: String): Maybe<User>
    fun deleteUser(id: UUID): Maybe<Unit>

    fun createWorkspace(userId: UUID, name: String): Maybe<UserWorkspace>
    fun renameWorkspace(userId: UUID, id: UUID, newName: String): Maybe<UserWorkspace>
    fun deleteWorkspace(userId: UUID, id: UUID): Maybe<Unit>

    fun moveAppInstallation(userId: UUID, workspaceId: UUID, appInstallationId: UUID, newWorkspaceId: UUID): Maybe<UserWorkspace>
}

interface AppInstallationCommandPort {
    fun installApp(userId: UUID, workspaceId: UUID, appId: UUID, appVersion: Semver): Maybe<AppInstallation>
    fun nameAppInstallation(userId: UUID, workspaceId: UUID, appInstallationId: UUID, nameSupplement: String?): Maybe<AppInstallation>
    fun updateAppInstallation(userId: UUID, workspaceId: UUID, appInstallationId: UUID, newVersion: Semver): Maybe<AppInstallation>
    fun uninstallApp(userId: UUID, workspaceId: UUID, appInstallationId: UUID): Maybe<Unit>
}

internal class UserCommandAdapter(
    private val userQueryPersistence: UserQueryPersistencePort,
    private val userCommandPersistence: UserCommandPersistencePort,
) : UserCommandPort {

    override fun registerUser(email: String, name: String): Maybe<User> {

        // TODO #22 check if registration enabled/allowed

        val userByEmailResult = userQueryPersistence.getByEmail(email)
        val userExists = userByEmailResult is Result && userByEmailResult.value != null
        if (userExists) {
            return Error(
                code = UserErrorCodes.REGISTRATION_EMAIL_EXISTS,
                details = null,
            )
        }

        return User.create(email, name).transform {
            userCommandPersistence.upsert(it)
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

    override fun moveAppInstallation(userId: UUID, workspaceId: UUID, appInstallationId: UUID, newWorkspaceId: UUID) =
        userId.loadUserAndInvoke({ it.moveAppInstallation(workspaceId, appInstallationId, newWorkspaceId) }) { user, _ ->
            userCommandPersistence.upsert(user).map {
                it.workspaces.first { workspace ->
                    workspace.id == newWorkspaceId
                }
            }
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
}

internal class AppInstallationCommandAdapter(
    private val userQueryPersistence: UserQueryPersistencePort,
    private val userWorkspaceQueryPersistence: UserWorkspaceQueryPersistencePort,
    private val userCommandPersistence: UserCommandPersistencePort,
    private val userWorkspaceCommandPersistence: UserWorkspaceCommandPersistencePort,
) : AppInstallationCommandPort {

    override fun installApp(userId: UUID, workspaceId: UUID, appId: UUID, appVersion: Semver): Maybe<AppInstallation> =
        (userId to workspaceId).loadWorkspaceAndInvoke({ it.installApp(appId, appVersion) }) { userWorkspace, _ ->
            userWorkspaceCommandPersistence.upsert(userId, userWorkspace).map {
                it.appInstallations.first { appInstallation ->
                    appInstallation.appId == appId && appInstallation.version == appVersion
                }
            }
        }

    override fun nameAppInstallation(userId: UUID, workspaceId: UUID, appInstallationId: UUID, nameSupplement: String?) =
        (userId to workspaceId).loadWorkspaceAndInvoke({ it.nameAppInstallation(appInstallationId, nameSupplement) }) { userWorkspace, _ ->
            userWorkspaceCommandPersistence.upsert(userId, userWorkspace).map {
                it.appInstallations.first { appInstallation ->
                    appInstallation.id == appInstallationId
                }
            }
        }

    override fun updateAppInstallation(userId: UUID, workspaceId: UUID, appInstallationId: UUID, newVersion: Semver): Maybe<AppInstallation> =
        (userId to workspaceId).loadWorkspaceAndInvoke({ it.updateAppInstallation(appInstallationId, newVersion) }) { userWorkspace, _ ->
            userWorkspaceCommandPersistence.upsert(userId, userWorkspace).map {
                it.appInstallations.first { appInstallation ->
                    appInstallation.id == appInstallationId
                }
            }
        }

    override fun uninstallApp(userId: UUID, workspaceId: UUID, appInstallationId: UUID): Maybe<Unit> =
        (userId to workspaceId).loadWorkspaceAndInvoke({ it.uninstallApp(appInstallationId) }) { userWorkspace, _ ->
            userWorkspaceCommandPersistence.upsert(userId, userWorkspace)
        }.map { }

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
}
