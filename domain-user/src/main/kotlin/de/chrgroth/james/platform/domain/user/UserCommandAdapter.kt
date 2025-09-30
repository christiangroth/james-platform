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
import io.quarkus.arc.DefaultBean
import io.quarkus.vertx.ConsumeEvent
import io.smallrye.common.annotation.Blocking
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

// TODO run checks in parallel
// TODO validate password policy

@Singleton
class UserDomainConfig {

  @ConfigProperty(name = "domain.user.defaultAdminUsername")
  lateinit var adminUsername: String

  @ConfigProperty(name = "domain.user.defaultAdminPassword")
  lateinit var adminPassword: String

  @ConfigProperty(name = "domain.user.defaultUsers")
  lateinit var defaultUsers: List<String>

  @Produces
  @DefaultBean
  fun produceDefaultUsers(): List<DefaultUser> {
    return defaultUsers.map { userConfig ->
      val parts = userConfig.split('|')

      val name = parts[0]
      checkNotNull(name) { "Default user name must be provided!" }

      val role = parts[1]
      checkNotNull(role) { "Default user role must be provided!" }

      DefaultUser(
        name = name,
        role = UserRole.valueOf(role)
      )
    }
  }
}

data class DefaultUser(
  val name: String,
  val role: UserRole,
)

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
