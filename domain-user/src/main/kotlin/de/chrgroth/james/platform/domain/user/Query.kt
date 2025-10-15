package de.chrgroth.james.platform.domain.user

import arrow.core.ValidatedNel
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
}
