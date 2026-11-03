package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.ReportDateRange
import de.chrgroth.james.platform.domain.model.app.TimeBucket
import de.chrgroth.james.platform.domain.model.readmodel.encodeTimeBucket
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay
import java.time.YearMonth
import java.time.temporal.WeekFields

/** Periods of time bucket keys (see `encodeTimeBucket`): the dates a bucket covers, and the matching bucket one year earlier. */
internal object ReportBuckets {

  private val WEEK = Regex("""(\d{4})-W(\d{2})""")
  private val QUARTER = Regex("""(\d{4})-Q(\d)""")
  private val CALENDAR_START: MonthDay = MonthDay.of(1, 1)
  private const val MONTHS_PER_QUARTER = 3L

  /** The period (both bounds inclusive) of bucket [key]; JAHR and QUARTAL count from [periodStart]. Null for a key that is no key of [bucket]. */
  fun period(bucket: TimeBucket, key: String, periodStart: MonthDay?): ReportDateRange? {
    val start = start(bucket, key, periodStart) ?: return null
    val next = when (bucket) {
      TimeBucket.TAG -> start.plusDays(1)
      TimeBucket.WOCHE -> start.plusWeeks(1)
      TimeBucket.MONAT -> start.plusMonths(1)
      TimeBucket.QUARTAL -> start.plusMonths(MONTHS_PER_QUARTER)
      TimeBucket.JAHR -> start.plusYears(1)
    }
    return ReportDateRange(start, next.minusDays(1))
  }

  /** The key of the bucket one year before [key], e.g. `2026-09` -> `2025-09`, `2026-W10` -> `2025-W10`. */
  fun previousYear(bucket: TimeBucket, key: String, periodStart: MonthDay?): String? {
    if (bucket == TimeBucket.WOCHE) {
      return WEEK.matchEntire(key)?.let { "%04d-W%s".format(it.groupValues[1].toInt() - 1, it.groupValues[2]) }
    }
    val start = start(bucket, key, periodStart) ?: return null
    return encodeTimeBucket(bucket, start.minusYears(1), periodStart)
  }

  private fun start(bucket: TimeBucket, key: String, periodStart: MonthDay?): LocalDate? = runCatching {
    val anchor = periodStart ?: CALENDAR_START
    when (bucket) {
      TimeBucket.TAG -> LocalDate.parse(key)
      TimeBucket.WOCHE -> WEEK.matchEntire(key)?.let { match ->
        LocalDate.of(match.groupValues[1].toInt(), 1, 4)
          .with(WeekFields.ISO.weekOfWeekBasedYear(), match.groupValues[2].toLong())
          .with(DayOfWeek.MONDAY)
      }
      TimeBucket.MONAT -> YearMonth.parse(key).atDay(1)
      TimeBucket.QUARTAL -> QUARTER.matchEntire(key)?.let { match ->
        LocalDate.of(match.groupValues[1].toInt(), anchor.monthValue, anchor.dayOfMonth)
          .plusMonths((match.groupValues[2].toLong() - 1) * MONTHS_PER_QUARTER)
      }
      TimeBucket.JAHR -> LocalDate.of(key.toInt(), anchor.monthValue, anchor.dayOfMonth)
    }
  }.getOrNull()
}
