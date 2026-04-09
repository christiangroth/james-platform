package de.chrgroth.spotify.control.domain.port.`in`.user

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.user.UserId

interface LoginServicePort {
  fun isAllowed(userId: UserId): Boolean
  fun handleCallback(code: String): Either<DomainError, UserId>
}
