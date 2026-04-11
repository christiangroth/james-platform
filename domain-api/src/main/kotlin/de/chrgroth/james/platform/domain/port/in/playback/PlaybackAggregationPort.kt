package de.chrgroth.james.platform.domain.port.`in`.playback

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import kotlinx.datetime.LocalDate

interface PlaybackAggregationPort {
  fun enqueueAggregateDay(date: LocalDate)
  fun enqueueAggregateWeek(weekStart: LocalDate)
  fun enqueueAggregateMonth(monthStart: LocalDate)
  fun enqueueAggregateQuarter(quarterStart: LocalDate)
  fun enqueueAggregateYear(yearStart: LocalDate)
  fun rebuildAllAggregations()
  fun handle(event: DomainOutboxEvent.AggregatePlaybackData): Either<DomainError, Unit>
}
