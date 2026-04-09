package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.quarkus.starters.domain.ScheduledSkipPredicate
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackAggregationPort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.datetime.LocalDate as KLocalDate
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class PlaybackAggregationJob(
  private val aggregation: PlaybackAggregationPort,
) {

  @Scheduled(cron = "0 0 1 * * ?", skipExecutionIf = ScheduledSkipPredicate::class)
  fun aggregateDaily() {
    val yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1)
    logger.info { "Triggering day aggregation for $yesterday" }
    aggregation.enqueueAggregateDay(yesterday.toKotlin())
  }

  @Scheduled(cron = "0 30 1 ? * MON", skipExecutionIf = ScheduledSkipPredicate::class)
  fun aggregateWeekly() {
    val previousMonday = LocalDate.now(ZoneOffset.UTC).minusWeeks(1)
    logger.info { "Triggering week aggregation starting $previousMonday" }
    aggregation.enqueueAggregateWeek(previousMonday.toKotlin())
  }

  @Scheduled(cron = "0 0 2 1 * ?", skipExecutionIf = ScheduledSkipPredicate::class)
  fun aggregateMonthly() {
    val firstOfPreviousMonth = LocalDate.now(ZoneOffset.UTC).minusMonths(1).withDayOfMonth(1)
    logger.info { "Triggering month aggregation for $firstOfPreviousMonth" }
    aggregation.enqueueAggregateMonth(firstOfPreviousMonth.toKotlin())
  }

  @Scheduled(cron = "0 30 2 1 1,4,7,10 ?", skipExecutionIf = ScheduledSkipPredicate::class)
  fun aggregateQuarterly() {
    val firstOfPreviousQuarter = LocalDate.now(ZoneOffset.UTC).minusMonths(MONTHS_PER_QUARTER).withDayOfMonth(1)
    logger.info { "Triggering quarter aggregation starting $firstOfPreviousQuarter" }
    aggregation.enqueueAggregateQuarter(firstOfPreviousQuarter.toKotlin())
  }

  @Scheduled(cron = "0 0 3 1 1 ?", skipExecutionIf = ScheduledSkipPredicate::class)
  fun aggregateYearly() {
    val firstOfPreviousYear = LocalDate.now(ZoneOffset.UTC).minusYears(1).withDayOfMonth(1)
    logger.info { "Triggering year aggregation for $firstOfPreviousYear" }
    aggregation.enqueueAggregateYear(firstOfPreviousYear.toKotlin())
  }

  companion object : KLogging() {
    private const val MONTHS_PER_QUARTER = 3L
  }
}

private fun LocalDate.toKotlin(): KLocalDate = KLocalDate(year, monthValue, dayOfMonth)
