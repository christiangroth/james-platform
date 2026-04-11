package de.chrgroth.james.platform.domain.port.out.playback

import de.chrgroth.james.platform.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.james.platform.domain.model.playback.aggregation.PlaybackAggregation
import de.chrgroth.james.platform.domain.model.user.UserId
import kotlinx.datetime.LocalDate

interface PlaybackAggregationRepositoryPort {
  fun save(aggregation: PlaybackAggregation)
  fun deleteAll()
  fun findByUserAndPeriod(userId: UserId, type: AggregationPeriodType, periodStart: LocalDate): PlaybackAggregation?
  fun findByUserTypeAndPeriodRange(userId: UserId, type: AggregationPeriodType, from: LocalDate, to: LocalDate): List<PlaybackAggregation>
}
