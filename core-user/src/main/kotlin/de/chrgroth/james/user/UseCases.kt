package de.chrgroth.james.user

import arrow.core.Validated
import arrow.core.ValidatedNel
import arrow.core.andThen
import java.util.UUID

// TODO #22 need to check if user is active

interface UserAdminUseCases {
    fun registerUser(email: String, name: String): ValidatedNel<de.chrgroth.james.Error, User>
    fun deleteUser(id: UUID): ValidatedNel<de.chrgroth.james.Error, Unit>
}

interface UserSelfServiceUseCases {
    fun changeEmail(id: UUID, email: String): ValidatedNel<de.chrgroth.james.Error, User>
    fun changeName(id: UUID, name: String): ValidatedNel<de.chrgroth.james.Error, User>
}

internal class UserAdminUseCasesService(
    private val queryPersistence: UserQueryPersistencePort,
    private val commandPersistence: UserCommandPersistencePort,
) : UserAdminUseCases {

    // TODO #22 check if registration enabled/allowed
    override fun registerUser(email: String, name: String): ValidatedNel<de.chrgroth.james.Error, User> =
        User.create(email = email, name = name).andThen {
            it.email.ensureUserNotPresent(queryPersistence) {
                commandPersistence.upsert(it)
            }
        }

    // TODO #22 define when User deletion is supported
    override fun deleteUser(id: UUID): ValidatedNel<de.chrgroth.james.Error, Unit> =
        queryPersistence.getOrError(id).andThen {
            Validated.invalidNel(
                de.chrgroth.james.Error(
                    code = UserErrorCodes.DELETE_NOT_SUPPORTED,
                    details = null,
                )
            )
        }
}

internal class UserSelfServiceUseCasesService(
    private val queryPersistence: UserQueryPersistencePort,
    private val commandPersistence: UserCommandPersistencePort,
) : UserSelfServiceUseCases {

    override fun changeEmail(id: UUID, email: String): ValidatedNel<de.chrgroth.james.Error, User> =
        queryPersistence.getOrError(id).andThen {
            it.changeEmail(email)
        }.andThen {
            it.email.ensureUserNotPresent(queryPersistence) {
                commandPersistence.upsert(it)
            }
        }

    override fun changeName(id: UUID, name: String): ValidatedNel<de.chrgroth.james.Error, User> =
        queryPersistence.getOrError(id).andThen {
            it.changeName(name)
        }.andThen {
            commandPersistence.upsert(it)
        }
}

private fun String.ensureUserNotPresent(
    queryPersistence: UserQueryPersistencePort,
    operation: () -> ValidatedNel<de.chrgroth.james.Error, User>,
): ValidatedNel<de.chrgroth.james.Error, User> =
    queryPersistence.getByEmail(this).andThen { existingUser ->
        if (existingUser == null) {
            operation.invoke()
        } else {
            Validated.invalidNel(
                de.chrgroth.james.Error(
                    code = UserErrorCodes.EMAIL_EXISTS,
                    details = this,
                )
            )
        }
    }
