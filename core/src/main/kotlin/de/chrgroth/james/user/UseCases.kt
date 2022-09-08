package de.chrgroth.james.user

import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import java.util.UUID

// TODO #22 need to check if user is active

interface UserAdminUseCases {
    fun registerUser(email: String, name: String): Maybe<User>
    fun deleteUser(id: UUID): Maybe<Unit>
}

interface UserSelfServiceUseCases {
    fun changeEmail(id: UUID, email: String): Maybe<User>
    fun changeName(id: UUID, name: String): Maybe<User>
}

internal class UserAdminUseCasesService(
    private val queryPersistence: UserQueryPersistencePort,
    private val commandPersistence: UserCommandPersistencePort,
) : UserAdminUseCases {

    // TODO #22 check if registration enabled/allowed
    override fun registerUser(email: String, name: String): Maybe<User> =
        User.create(email, name).flatMap {
            it.email.ensureUserNotPresent(queryPersistence) {
                commandPersistence.upsert(it)
            }
        }

    // TODO #22 define when User deletion is supported
    override fun deleteUser(id: UUID): Maybe<Unit> =
        queryPersistence.getOrError(id).flatMap {
            Error(
                code = UserErrorCodes.DELETE_NOT_SUPPORTED,
                details = null,
            )
        }
}

internal class UserSelfServiceUseCasesService(
    private val queryPersistence: UserQueryPersistencePort,
    private val commandPersistence: UserCommandPersistencePort,
) : UserSelfServiceUseCases {

    override fun changeEmail(id: UUID, email: String): Maybe<User> =
        queryPersistence.getOrError(id).flatMap {
            it.changeEmail(email)
        }.flatMap {
            it.email.ensureUserNotPresent(queryPersistence) {
                commandPersistence.upsert(it)
            }
        }

    override fun changeName(id: UUID, name: String) =
        queryPersistence.getOrError(id).flatMap {
            it.changeName(name)
        }.flatMap {
            commandPersistence.upsert(it)
        }
}

private fun String.ensureUserNotPresent(queryPersistence: UserQueryPersistencePort, operation: () -> Maybe<User>): Maybe<User> =
    queryPersistence.getByEmail(this).flatMap { existingUser ->
        if (existingUser == null) {
            operation.invoke()
        } else {
            Error(
                code = UserErrorCodes.EMAIL_EXISTS,
                details = this,
            )
        }
    }
