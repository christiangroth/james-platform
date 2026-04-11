package de.chrgroth.spotify.control.domain.port.`in`.user

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.user.User

interface LoginServicePort {
  fun login(username: String, password: String): Either<DomainError, User>
}
