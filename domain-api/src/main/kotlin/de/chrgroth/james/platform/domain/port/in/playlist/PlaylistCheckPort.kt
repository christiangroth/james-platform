package de.chrgroth.james.platform.domain.port.`in`.playlist

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent

interface PlaylistCheckPort {
  fun handle(event: DomainOutboxEvent.RunPlaylistChecks): Either<DomainError, Unit>
  fun getDisplayNames(): Map<String, String>
  fun getFixableCheckIds(): Set<String>
  fun runFix(userId: UserId, playlistId: String, checkType: String): Either<DomainError, Unit>
}
