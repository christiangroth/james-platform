package de.chrgroth.james.platform.domain.user.port.out

import arrow.core.ValidatedNel
import de.chrgroth.james.DomainError
import de.chrgroth.james.platform.domain.user.User
import de.chrgroth.james.platform.domain.user.UserId

// TODO work on return types and errors or ignoring them
interface UserPersistencePort {
  fun byId(id: UserId): ValidatedNel<DomainError, User?>
  fun byUsername(username: String): ValidatedNel<DomainError, User?>
  fun all(): ValidatedNel<DomainError, Set<User>>
  fun create(user: User): ValidatedNel<DomainError, Unit>

  // TODO ignores if not existent, return error
  fun update(user: User): ValidatedNel<DomainError, Unit>
  fun delete(id: UserId): ValidatedNel<DomainError, Unit>
}
