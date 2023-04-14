package de.chrgroth.james.user

import arrow.core.NonEmptyList
import arrow.core.Validated
import arrow.core.ValidatedNel
import arrow.core.andThen
import de.chrgroth.james.DomainError
import de.chrgroth.james.DomainEvent
import de.chrgroth.james.EventBus
import java.util.UUID

// TODO #6 need to check if user is active

interface UserAdminUseCases {
    fun registerUser(email: String, name: String): ValidatedNel<DomainError, User>
    fun deleteUser(id: UUID): ValidatedNel<DomainError, Unit>
}

interface UserSelfServiceUseCases {
    fun changeEmail(id: UUID, email: String): ValidatedNel<DomainError, User>
    fun changeName(id: UUID, name: String): ValidatedNel<DomainError, User>
}

internal class UserAdminUseCasesService(
    private val queryPersistence: UserQueryPersistencePort,
    private val commandPersistence: UserCommandPersistencePort,
    private val eventBus: EventBus,
) : UserAdminUseCases {

    // TODO #6 check if registration enabled/allowed
    override fun registerUser(email: String, name: String): ValidatedNel<DomainError, User> =
        User.create(email = email, name = name).andThen { user ->
            user.email.ensureUserNotPresent(queryPersistence) {
                commandPersistence.upsert(user).also {
                    eventBus.publish(DomainEvent.UserRegistered(user.id))
                }
            }
        }

    // TODO #6 define when User deletion is supported
    override fun deleteUser(id: UUID): ValidatedNel<DomainError, Unit> =
        queryPersistence.getOrError(id).andThen<NonEmptyList<DomainError>, User, Unit> {
            Validated.invalidNel(DomainError(code = UserDomainErrorCodes.DELETE_NOT_SUPPORTED))
        }
}

internal class UserSelfServiceUseCasesService(
    private val queryPersistence: UserQueryPersistencePort,
    private val commandPersistence: UserCommandPersistencePort,
) : UserSelfServiceUseCases {

    override fun changeEmail(id: UUID, email: String): ValidatedNel<DomainError, User> =
        queryPersistence.getOrError(id).andThen {
            it.changeEmail(email)
        }.andThen {
            it.email.ensureUserNotPresent(queryPersistence) {
                commandPersistence.upsert(it)
            }
        }

    override fun changeName(id: UUID, name: String): ValidatedNel<DomainError, User> =
        queryPersistence.getOrError(id).andThen {
            it.changeName(name)
        }.andThen {
            commandPersistence.upsert(it)
        }
}

private fun String.ensureUserNotPresent(
    queryPersistence: UserQueryPersistencePort,
    operation: () -> ValidatedNel<DomainError, User>,
): ValidatedNel<DomainError, User> =
    queryPersistence.getByEmail(this).andThen { existingUser ->
        if (existingUser == null) {
            operation.invoke()
        } else {
            Validated.invalidNel(DomainError(code = UserDomainErrorCodes.EMAIL_EXISTS))
        }
    }
