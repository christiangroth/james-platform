package de.chrgroth.james.platform.domain.port.`in`.user

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.user.User

interface LoginServicePort {
  fun login(username: String, password: String): Either<DomainError, User>
}
