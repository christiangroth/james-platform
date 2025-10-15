package de.chrgroth.james.platform.domain.user

import arrow.core.ValidatedNel
import arrow.core.andThen
import arrow.core.invalidNel
import arrow.core.validNel
import arrow.core.zip
import de.chrgroth.james.DomainError
import de.chrgroth.james.platform.domain.user.port.`in`.DomainUserEvents
import de.chrgroth.james.platform.domain.user.port.`in`.EVENT_TOPIC_TO_DOMAIN_USER
import de.chrgroth.james.platform.domain.user.port.`in`.UserCommandPort
import de.chrgroth.james.platform.domain.user.port.out.UserPersistencePort
import io.quarkus.vertx.ConsumeEvent
import io.smallrye.common.annotation.Blocking
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import mu.KLogging

// TODO run checks in parallel
// TODO validate password policy

@ApplicationScoped
@Suppress("Unused")
internal class UserCommandAdapter : UserCommandPort {

  @Inject
  private lateinit var persistence: UserPersistencePort

  @Inject
  private lateinit var config: UserDomainConfig

  @Inject
  lateinit var defaultUsers: List<DefaultUser>

  @Blocking
  @Transactional
  @ConsumeEvent(EVENT_TOPIC_TO_DOMAIN_USER)
  fun consume(@Suppress("Unused", "UnusedParameter") event: DomainUserEvents.PersistenceInitialized) {
    persistence.all().map { users ->
      if (users.isEmpty() || users.none { it.roles.contains(UserRole.ADMIN) }) {
        logger.info { "No user with admin role found, creating default admin user..." }
        register(
          username = config.adminUsername,
          password = config.adminPassword,
          roles = setOf(UserRole.ADMIN)
        )
      } else {
        Unit.validNel()
      }
    }.fold({
      logger.error { "Unable to ensure admin user: ${it.map { error -> error.toLogString() }}" }
    }, {
      logger.info { "Please login and change password for automatically created admin user: ${config.adminUsername} / ${config.adminPassword}" }
    })

    defaultUsers.forEach { defaultUser ->
      persistence.byUsername(defaultUser.name).map { user ->
        if (user == null) {
          logger.info { "No default user with name ${defaultUser.name} found, creating ..." }
          register(
            username = defaultUser.name,
            password = defaultUser.name,
            roles = setOf(defaultUser.role)
          )
        } else {
          logger.info { "Default user with name ${defaultUser.name} already exists." }
          Unit.validNel()
        }
      }.fold({
        logger.error { "Unable to ensure default user ${defaultUser.name}: ${it.map { error -> error.toLogString() }}" }
      }, {
        logger.info { "Please login and change password for automatically created user: ${defaultUser.name} / ${defaultUser.name}" }
      })
    }
  }

  override fun authenticate(username: String, password: String): ValidatedNel<DomainError, User> {
    val passwordHashCheck = password.hashPassword()
    val persistenceCheck = persistence.byUsername(username).andThen {
      it?.validNel()
        ?: DomainError(
          code = UserDomainErrorCodes.USER_UNKNOWN,
          errorMessage = null,
        ).invalidNel()
    }

    return passwordHashCheck.zip(persistenceCheck) { passwordHash, user ->
      user to passwordHash
    }.andThen { (user, passwordHash) ->
      if (user.passwordHash == passwordHash) {
        user.validNel()
      } else {
        DomainError(
          code = UserDomainErrorCodes.USER_AUTHENTICATION_FAILED,
          errorMessage = null,
        ).invalidNel()
      }
    }
  }

  override fun register(username: String, password: String, roles: Set<UserRole>): ValidatedNel<DomainError, Unit> {
    val persistenceCheck = persistence.byUsername(username).andThen {
      if (it != null) {
        DomainError(
          code = UserDomainErrorCodes.REGISTRATION_USERNAME_EXISTS,
          errorMessage = null,
        ).invalidNel()
      } else {
        ValidatedNel.validNel(Unit)
      }
    }

    val userCheck = password.hashPassword().andThen { passwordHash ->
      User(
        username = username,
        passwordHash = passwordHash,
        roles = roles,
      )
    }

    return persistenceCheck.zip(userCheck) { _, validUser ->
      validUser
    }.andThen {
      persistence.create(it)
    }
  }

  override fun changePassword(id: UserId, password: String): ValidatedNel<DomainError, Unit> {
    val passwordHashCheck = password.hashPassword()
    val persistenceCheck = ensureUser(id)

    return passwordHashCheck.zip(persistenceCheck) { passwordHash, user ->
      user to passwordHash
    }.andThen { (user, passwordHash) ->
      user.changePassword(passwordHash)
    }.andThen {
      persistence.update(it)
    }
  }

  override fun resetPassword(id: UserId, password: String): ValidatedNel<DomainError, Unit> {
    val passwordHashCheck = password.hashPassword()
    val persistenceCheck = ensureUser(id)

    return passwordHashCheck.zip(persistenceCheck) { passwordHash, user ->
      user to passwordHash
    }.andThen { (user, passwordHash) ->
      user.resetPassword(passwordHash)
    }.andThen {
      persistence.update(it)
    }
  }

  // TODO create password hash
  private fun String.hashPassword(): ValidatedNel<DomainError, String> {
    return this.validNel()
  }

  override fun changeUsername(id: UserId, username: String): ValidatedNel<DomainError, Unit> =
    ensureUser(id)
      .andThen { user ->
        persistence.byUsername(username).andThen {
          if (it != null) {
            DomainError(
              code = UserDomainErrorCodes.REGISTRATION_USERNAME_EXISTS,
              errorMessage = null,
            ).invalidNel()
          } else {
            user.changeUsername(username)
          }
        }
      }.andThen {
        persistence.update(it)
      }

  override fun changeRoles(id: UserId, roles: Set<UserRole>): ValidatedNel<DomainError, Unit> =
    ensureUser(id)
      .andThen { user ->
        user.changeRoles(roles)
      }.andThen {
        persistence.update(it)
      }

  override fun deactivate(id: UserId, statusReason: String): ValidatedNel<DomainError, Unit> =
    ensureUser(id)
      .andThen {
        it.deactivate(statusReason)
      }.andThen {
        persistence.update(it)
      }

  override fun activate(id: UserId): ValidatedNel<DomainError, Unit> =
    ensureUser(id)
      .andThen {
        it.activate()
      }.andThen {
        persistence.update(it)
      }

  override fun delete(id: UserId): ValidatedNel<DomainError, Unit> =
    ensureUser(id)
      .andThen {
        persistence.delete(it.id)
      }

  private fun ensureUser(id: UserId): ValidatedNel<DomainError, User> =
    persistence.byId(id).andThen {
      it?.validNel()
        ?: DomainError(
          code = UserDomainErrorCodes.USER_UNKNOWN,
          errorMessage = null,
        ).invalidNel()
    }

  companion object : KLogging()
}
