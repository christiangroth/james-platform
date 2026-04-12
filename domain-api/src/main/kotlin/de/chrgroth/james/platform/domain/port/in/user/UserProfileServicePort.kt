package de.chrgroth.james.platform.domain.port.`in`.user

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.user.User

interface UserProfileServicePort {
  fun getProfile(username: String): Either<DomainError, User>
  fun changeUsername(currentUsername: String, newUsername: String): Either<DomainError, User>
  fun changePassword(username: String, currentPassword: String, newPassword: String): Either<DomainError, User>
}
