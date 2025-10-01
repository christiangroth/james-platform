package de.chrgroth.james.platform.adapter.out.postgres.user

import arrow.core.ValidatedNel
import arrow.core.invalidNel
import arrow.core.valid
import de.chrgroth.james.DomainError
import de.chrgroth.james.platform.adapter.out.postgres.user.jooq.tables.Users.USERS
import de.chrgroth.james.platform.adapter.out.postgres.user.jooq.tables.records.UsersRecord
import de.chrgroth.james.platform.domain.user.PasswordStatus
import de.chrgroth.james.platform.domain.user.User
import de.chrgroth.james.platform.domain.user.UserDomainErrorCodes
import de.chrgroth.james.platform.domain.user.UserId
import de.chrgroth.james.platform.domain.user.UserRole
import de.chrgroth.james.platform.domain.user.UserStatus
import de.chrgroth.james.platform.domain.user.port.`in`.DomainUserEvents
import de.chrgroth.james.platform.domain.user.port.`in`.EVENT_TOPIC_TO_DOMAIN_USER
import de.chrgroth.james.platform.domain.user.port.out.UserPersistencePort
import io.quarkus.runtime.StartupEvent
import io.vertx.core.eventbus.EventBus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import mu.KLogging
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException

@ApplicationScoped
@Suppress("Unused")
class UserPersistenceAdapterJooq : UserPersistencePort {

  @Inject
  private lateinit var eventBus: EventBus

  @Inject
  @UserDatabase
  private lateinit var dsl: DSLContext

  @Suppress("Unused")
  fun startup(@Observes @Suppress("UnusedParameter") event: StartupEvent) {
    logger.info { "UserDatabase JOOQ adapter initialized." }
    eventBus.publish(EVENT_TOPIC_TO_DOMAIN_USER, DomainUserEvents.PersistenceInitialized)
  }

  override fun byId(id: UserId): ValidatedNel<DomainError, User?> = runCatching {
    dsl.selectFrom(USERS)
      .where(USERS.ID.eq(id.value))
      .fetchOne()
      ?.toDomain()
  }.fold(
    onSuccess = { it.valid() },
    onFailure = {
      logger.error(it) { "Database operation failed" }
      DomainError(
        code = UserDomainErrorCodes.USER_QUERY_FAILED,
        errorMessage = it.message
      ).invalidNel()
    }
  )

  override fun byUsername(username: String): ValidatedNel<DomainError, User?> = runCatching {
    dsl.selectFrom(USERS)
      .where(USERS.USERNAME.eq(username))
      .fetchOne()
      ?.toDomain()
  }.fold(
    onSuccess = { it.valid() },
    onFailure = {
      logger.error(it) { "Database operation failed" }
      DomainError(
        code = UserDomainErrorCodes.USER_QUERY_FAILED,
        errorMessage = it.message
      ).invalidNel()
    }
  )

  override fun all(): ValidatedNel<DomainError, Set<User>> = runCatching {
    dsl.selectFrom(USERS)
      .fetch()
      .map { it.toDomain() }
      .toSet()
  }.fold(
    onSuccess = { it.valid() },
    onFailure = {
      logger.error(it) { "Database operation failed" }
      DomainError(
        code = UserDomainErrorCodes.USER_QUERY_FAILED,
        errorMessage = it.message
      ).invalidNel()
    }
  )

  override fun create(user: User): ValidatedNel<DomainError, Unit> = runCatching {
    val record = UsersRecord(
      user.id.value,
      user.username,
      user.passwordHash,
      user.passwordStatus.name,
      user.roles.map { it.name }.toTypedArray(),
      user.status.name,
      user.statusReason,
      user.deactivationCounter.toInt()
    )
    dsl.executeInsert(record)
    Unit
  }.fold(
    onSuccess = { Unit.valid() },
    onFailure = {
      if (it is DataAccessException && it.message?.contains("duplicate key") == true) {
        DomainError(
          code = UserDomainErrorCodes.REGISTRATION_USERNAME_EXISTS,
          errorMessage = "User with username already exists"
        ).invalidNel()
      } else {
        logger.error(it) { "Database operation failed" }
        DomainError(
          code = UserDomainErrorCodes.USER_QUERY_FAILED,
          errorMessage = it.message
        ).invalidNel()
      }
    }
  )

  override fun update(user: User): ValidatedNel<DomainError, Unit> = runCatching {
    val updated = dsl.update(USERS)
      .set(USERS.USERNAME, user.username)
      .set(USERS.PASSWORD_HASH, user.passwordHash)
      .set(USERS.PASSWORD_STATUS, user.passwordStatus.name)
      .set(USERS.ROLES, user.roles.map { it.name }.toTypedArray())
      .set(USERS.STATUS, user.status.name)
      .set(USERS.STATUS_REASON, user.statusReason)
      .set(USERS.DEACTIVATION_COUNTER, user.deactivationCounter.toInt())
      .where(USERS.ID.eq(user.id.value))
      .execute()

    if (updated == 0) {
      DomainError(code = UserDomainErrorCodes.USER_UNKNOWN, errorMessage = "User not found").invalidNel()
    } else {
      Unit.valid()
    }
  }.fold(
    onSuccess = { it },
    onFailure = {
      logger.error(it) { "Database operation failed" }
      DomainError(
        code = UserDomainErrorCodes.USER_QUERY_FAILED,
        errorMessage = it.message
      ).invalidNel()
    }
  )

  override fun delete(id: UserId): ValidatedNel<DomainError, Unit> = runCatching {
    val deleted = dsl.deleteFrom(USERS)
      .where(USERS.ID.eq(id.value))
      .execute()

    if (deleted == 0) {
      DomainError(code = UserDomainErrorCodes.USER_UNKNOWN, errorMessage = "User not found").invalidNel()
    } else {
      Unit.valid()
    }
  }.fold(
    onSuccess = { it },
    onFailure = {
      logger.error(it) { "Database operation failed" }
      DomainError(
        code = UserDomainErrorCodes.USER_QUERY_FAILED,
        errorMessage = it.message
      ).invalidNel()
    }
  )

  private fun UsersRecord.toDomain(): User =
    User.fromEntity(
      id = UserId(id),
      username = username,
      passwordHash = passwordHash,
      passwordStatus = PasswordStatus.valueOf(passwordStatus),
      roles = roles.map { UserRole.valueOf(it) }.toSet(),
      status = UserStatus.valueOf(status),
      statusReason = statusReason,
      deactivationCounter = deactivationCounter.toUShort()
    )

  companion object : KLogging()
}
