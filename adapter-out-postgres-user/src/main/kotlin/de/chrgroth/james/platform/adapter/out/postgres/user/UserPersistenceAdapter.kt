package de.chrgroth.james.platform.adapter.out.postgres.user

import arrow.core.ValidatedNel
import arrow.core.invalidNel
import arrow.core.validNel
import de.chrgroth.james.DomainError
import de.chrgroth.james.platform.domain.user.PasswordStatus
import de.chrgroth.james.platform.domain.user.User
import de.chrgroth.james.platform.domain.user.UserDomainErrorCodes
import de.chrgroth.james.platform.domain.user.UserId
import de.chrgroth.james.platform.domain.user.UserRole
import de.chrgroth.james.platform.domain.user.UserStatus
import de.chrgroth.james.platform.domain.user.port.out.UserPersistencePort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import migrations.Users

@ApplicationScoped
@Suppress("Unused")
class UserPersistenceAdapter : UserPersistencePort {

  @Inject
  private lateinit var db: UserDatabase

  override fun byId(id: UserId): ValidatedNel<DomainError, User?> =
    runWithoutTransaction {
      findById(id.value).executeAsOneOrNull()?.toDomainObject()
    }

  override fun byUsername(username: String): ValidatedNel<DomainError, User?> =
    runWithoutTransaction {
      findByUsername(username).executeAsOneOrNull()?.toDomainObject()
    }

  override fun all(): ValidatedNel<DomainError, Set<User>> =
    runWithoutTransaction {
      findAll().executeAsList().map { it.toDomainObject() }.toSet()
    }

  override fun create(user: User): ValidatedNel<DomainError, Unit> =
    runInTransaction {
      create(
        id = user.id.value,
        username = user.username,
        passwordHash = user.passwordHash,
        passwordStatus = user.passwordStatus.name,
        roles = user.roles.map { it.name }.toSet(),
        status = user.status.name,
        statusReason = user.statusReason,
        deactivationCounter = user.deactivationCounter.toShort(),
      )
    }

  override fun update(user: User): ValidatedNel<DomainError, Unit> =
    runInTransaction {
      update(
        id = user.id.value,
        username = user.username,
        passwordHash = user.passwordHash,
        passwordStatus = user.passwordStatus.name,
        roles = user.roles.map { it.name }.toSet(),
        status = user.status.name,
        statusReason = user.statusReason,
        deactivationCounter = user.deactivationCounter.toShort(),
      )
    }

  override fun delete(id: UserId): ValidatedNel<DomainError, Unit> =
    runInTransaction {
      deleteById(id.value)
    }

  @Suppress("TooGenericExceptionCaught")
  private fun <T> runInTransaction(operation: UserQueries.() -> T): ValidatedNel<DomainError, T> =
    try {
      db.transactionWithResult {
        runWithoutTransaction(operation)
      }
    } catch (e: Exception) {
      DomainError(
        code = UserDomainErrorCodes.USER_QUERY_FAILED,
        errorMessage = e.localizedMessage
      ).invalidNel()
    }

  @Suppress("TooGenericExceptionCaught")
  private fun <T> runWithoutTransaction(operation: UserQueries.() -> T): ValidatedNel<DomainError, T> =
    try {
      db.userQueries.operation().validNel()
    } catch (e: Exception) {
      DomainError(
        code = UserDomainErrorCodes.USER_QUERY_FAILED,
        errorMessage = e.localizedMessage
      ).invalidNel()
    }
}

private fun Users.toDomainObject(): User =
  User.fromEntity(
    UserId(value = id),
    username,
    passwordHash,
    PasswordStatus.valueOf(passwordStatus),
    roles.map { UserRole.valueOf(it) }.toSet(),
    UserStatus.valueOf(status),
    statusReason,
    deactivationCounter.toUShort()
  )
