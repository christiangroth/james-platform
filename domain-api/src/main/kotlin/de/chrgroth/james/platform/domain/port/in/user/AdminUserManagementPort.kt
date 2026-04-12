package de.chrgroth.james.platform.domain.port.`in`.user

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserRole

interface AdminUserManagementPort {
  fun listUsers(): List<User>
  fun createUser(username: String, password: String, callingUsername: String): Either<DomainError, User>
  fun activateUser(username: String): Either<DomainError, User>
  fun deactivateUser(username: String, callingUsername: String): Either<DomainError, User>
  fun setPassword(username: String, newPassword: String): Either<DomainError, User>
  fun setRoles(username: String, roles: Set<UserRole>, callingUsername: String): Either<DomainError, User>
  fun deleteUser(username: String, callingUsername: String): Either<DomainError, Unit>
}
