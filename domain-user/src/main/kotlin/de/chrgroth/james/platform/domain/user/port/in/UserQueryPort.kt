package de.chrgroth.james.platform.domain.user.port.`in`

import arrow.core.ValidatedNel
import de.chrgroth.james.DomainError
import de.chrgroth.james.platform.domain.user.User
import de.chrgroth.james.platform.domain.user.UserId

interface UserQueryPort {
  fun all(): ValidatedNel<DomainError, Set<User>>
  fun byId(id: UserId): ValidatedNel<DomainError, User?>
  fun byUsername(username: String): ValidatedNel<DomainError, User?>
  fun authenticate(username: String, password: String): ValidatedNel<DomainError, User>
}
