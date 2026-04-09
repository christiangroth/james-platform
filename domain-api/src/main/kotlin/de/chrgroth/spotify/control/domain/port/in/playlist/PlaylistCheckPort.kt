package de.chrgroth.spotify.control.domain.port.`in`.playlist

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface PlaylistCheckPort {
  fun handle(event: DomainOutboxEvent.RunPlaylistChecks): Either<DomainError, Unit>
  fun getDisplayNames(): Map<String, String>
  fun getFixableCheckIds(): Set<String>
  fun runFix(userId: UserId, playlistId: String, checkType: String): Either<DomainError, Unit>
}
