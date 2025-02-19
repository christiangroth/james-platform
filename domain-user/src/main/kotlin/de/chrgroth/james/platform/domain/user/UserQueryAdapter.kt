package de.chrgroth.james.platform.domain.user

import arrow.core.ValidatedNel
import arrow.core.andThen
import arrow.core.invalidNel
import arrow.core.validNel
import arrow.core.zip
import de.chrgroth.james.DomainError
import de.chrgroth.james.platform.domain.user.port.`in`.UserQueryPort
import de.chrgroth.james.platform.domain.user.port.out.UserPersistencePort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
@Suppress("Unused")
internal class UserQueryAdapter : UserQueryPort {

  @Inject
  private lateinit var persistence: UserPersistencePort

  override fun all(): ValidatedNel<DomainError, Set<User>> {
    return persistence.all()
  }

  override fun byId(id: UserId): ValidatedNel<DomainError, User?> {
    return persistence.byId(id)
  }

  override fun byUsername(username: String): ValidatedNel<DomainError, User?> {
    return persistence.byUsername(username)
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
}
