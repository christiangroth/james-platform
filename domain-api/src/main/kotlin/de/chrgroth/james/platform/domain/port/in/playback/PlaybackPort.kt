package de.chrgroth.james.platform.domain.port.`in`.playback

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent

interface PlaybackPort {
  fun enqueueFetchPlaybackData()
  fun fetchPlaybackData(userId: UserId): Either<DomainError, Unit>
  fun enqueueRebuildPlaybackData(userId: UserId)
  fun rebuildPlaybackData(userId: UserId)
  fun appendPlaybackData(userId: UserId)
  fun handle(event: DomainOutboxEvent.FetchPlaybackData): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.RebuildPlaybackData): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.AppendPlaybackData): Either<DomainError, Unit>
}
