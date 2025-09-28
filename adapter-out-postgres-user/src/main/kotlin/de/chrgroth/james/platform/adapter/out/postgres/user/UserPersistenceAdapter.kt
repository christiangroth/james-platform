package de.chrgroth.james.platform.adapter.out.postgres.user

import arrow.core.ValidatedNel
import arrow.core.invalidNel
import de.chrgroth.james.DomainError
import de.chrgroth.james.platform.domain.user.User
import de.chrgroth.james.platform.domain.user.UserDomainErrorCodes
import de.chrgroth.james.platform.domain.user.UserId
import de.chrgroth.james.platform.domain.user.port.out.UserPersistencePort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jooq.exception.DataAccessException

@ApplicationScoped
@Suppress("Unused")
class UserPersistenceAdapter : UserPersistencePort {

  @Inject
  private lateinit var repository: UserRepository

  override fun byId(id: UserId): ValidatedNel<DomainError, User?> {
    return try {
      repository.byId(id)
    } catch (e: DataAccessException) {
      handleDatabaseError(e)
    }
  }

  override fun byUsername(username: String): ValidatedNel<DomainError, User?> {
    return try {
      repository.byUsername(username)
    } catch (e: DataAccessException) {
      handleDatabaseError(e)
    }
  }

  override fun all(): ValidatedNel<DomainError, Set<User>> {
    return try {
      repository.all()
    } catch (e: DataAccessException) {
      handleDatabaseError(e)
    }
  }

  override fun create(user: User): ValidatedNel<DomainError, Unit> {
    return try {
      repository.create(user)
    } catch (e: DataAccessException) {
      handleDatabaseError(e)
    }
  }

  override fun update(user: User): ValidatedNel<DomainError, Unit> {
    return try {
      repository.update(user)
    } catch (e: DataAccessException) {
      handleDatabaseError(e)
    }
  }

  override fun delete(id: UserId): ValidatedNel<DomainError, Unit> {
    return try {
      repository.delete(id)
    } catch (e: DataAccessException) {
      handleDatabaseError(e)
    }
  }

  private fun <T> handleDatabaseError(e: DataAccessException): ValidatedNel<DomainError, T> {
    return DomainError(
      code = UserDomainErrorCodes.USER_QUERY_FAILED,
      errorMessage = e.localizedMessage
    ).invalidNel()
  }
}
