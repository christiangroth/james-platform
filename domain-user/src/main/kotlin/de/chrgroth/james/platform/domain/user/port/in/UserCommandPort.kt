package de.chrgroth.james.platform.domain.user.port.`in`

import arrow.core.ValidatedNel
import de.chrgroth.james.DomainError
import de.chrgroth.james.platform.domain.user.UserId
import de.chrgroth.james.platform.domain.user.UserRole

interface UserCommandPort {
  fun register(username: String, password: String, roles: Set<UserRole>): ValidatedNel<DomainError, Unit>
  fun changePassword(id: UserId, password: String): ValidatedNel<DomainError, Unit>
  fun resetPassword(id: UserId, password: String): ValidatedNel<DomainError, Unit>
  fun deactivate(id: UserId, statusReason: String): ValidatedNel<DomainError, Unit>
  fun activate(id: UserId): ValidatedNel<DomainError, Unit>
  fun delete(id: UserId): ValidatedNel<DomainError, Unit>
}
