package de.chrgroth.james.user

import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import java.util.UUID

// TODO #22 need to check if user is active

interface UserCommandPort {
    fun registerUser(email: String, name: String): Maybe<User>
    fun changeEmail(id: UUID, email: String): Maybe<User>
    fun changeName(id: UUID, name: String): Maybe<User>
    fun deleteUser(id: UUID): Maybe<Unit>
}

internal class UserCommandAdapter(
    private val queryPersistence: UserQueryPersistencePort,
    private val commandPersistence: UserCommandPersistencePort,
) : UserCommandPort {

    override fun registerUser(email: String, name: String): Maybe<User> {

        // TODO #22 check if registration enabled/allowed

        // TODO #25 use map here!
        val userByEmailResult = queryPersistence.getByEmail(email)
        val userExists = userByEmailResult is Result && userByEmailResult.value != null
        if (userExists) {
            return Error(
                code = UserErrorCodes.EMAIL_EXISTS,
                details = email,
            )
        }

        return User.create(email, name).flatMap {
            commandPersistence.upsert(it)
        }
    }

    // TODO #25 ugly code
    override fun changeEmail(id: UUID, email: String): Maybe<User> {
        val userByEmailResult = queryPersistence.getByEmail(email)
        val userExists = userByEmailResult is Result && userByEmailResult.value != null
        if (userExists) {
            return Error(
                code = UserErrorCodes.EMAIL_EXISTS,
                details = email,
            )
        }

        return id.loadUserAndInvoke({ it.changeEmail(email) }) {
            commandPersistence.upsert(it)
        }
    }

    override fun changeName(id: UUID, name: String) =
        id.loadUserAndInvoke({ it.changeName(name) }) {
            commandPersistence.upsert(it)
        }

    override fun deleteUser(id: UUID) =
        id.loadUserAndInvoke(User::verifyDeletion) {
            commandPersistence.delete(id)
        }

    private fun <R, S> UUID.loadUserAndInvoke(
        userOperation: (User) -> Maybe<R>,
        persistenceOperation: (R) -> Maybe<S>,
    ) =
        queryPersistence.getOrError(this).flatMap { user ->
            userOperation(user).flatMap { persistenceOperation(it) }
        }
}
