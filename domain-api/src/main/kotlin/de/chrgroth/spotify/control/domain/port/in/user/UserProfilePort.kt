package de.chrgroth.spotify.control.domain.port.`in`.user

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface UserProfilePort {
  fun getDisplayName(userId: UserId): String?
  fun enqueueUpdates()
  fun update(userId: UserId): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.UpdateUserProfile): Either<DomainError, Unit>
}
