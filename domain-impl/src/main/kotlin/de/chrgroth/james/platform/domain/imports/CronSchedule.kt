package de.chrgroth.james.platform.domain.imports

import com.cronutils.model.Cron
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Validates and evaluates the Quartz-style cron expressions stored in [de.chrgroth.james.platform.domain.model.imports.ImportDefinition.schedule]
 * - the same dialect already used for `@Scheduled(cron = ...)` elsewhere in this codebase (e.g. `ImportJobCleanupJob`'s
 * `app.imports.cleanup.cron`), so a user-facing cron string means the same thing everywhere in the app. Unlike
 * `@Scheduled`, these expressions are per-[de.chrgroth.james.platform.domain.model.imports.ImportDefinition] and
 * evaluated dynamically against a stored `lastRunAt`, so they cannot be registered as static `@Scheduled` triggers.
 */
object CronSchedule {

  /** Matches `app.imports.schedule.poll-cron` (see `ImportDefinitionScheduleJob`): a definition's schedule cannot fire more often than the poller actually checks, or runs would silently be skipped down to poll granularity. */
  private val MIN_INTERVAL: Duration = Duration.ofMinutes(15)

  /** Number of consecutive occurrences sampled to check the minimum interval - enough to catch schedules with an uneven cadence (e.g. a fixed list of minutes with one short gap). */
  private const val SAMPLE_SIZE = 5

  private val parser = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ))

  fun isValid(expression: String): Boolean = try {
    val cron = parser.parse(expression)
    cron.validate()
    hasMinimumInterval(cron)
  } catch (e: IllegalArgumentException) {
    false
  }

  private fun hasMinimumInterval(cron: Cron): Boolean {
    val executionTime = ExecutionTime.forCron(cron)
    var since = Instant.EPOCH.atZone(ZoneOffset.UTC)
    var previous: ZonedDateTime? = null
    repeat(SAMPLE_SIZE) {
      val next = executionTime.nextExecution(since).orElse(null) ?: return true
      val lastExecution = previous
      if (lastExecution != null && Duration.between(lastExecution, next) < MIN_INTERVAL) return false
      previous = next
      since = next
    }
    return true
  }

  /**
   * True if [expression]'s next occurrence strictly after [since] (the definition's `lastRunAt`, or its `createdAt`
   * when it has never run) is at or before [now]. A definition whose schedule was just set on a long-existing
   * definition is therefore due on the very next poll - matching plain cron semantics, which have no "skip missed
   * occurrences" concept - and self-corrects afterwards once `lastRunAt` starts tracking real run times.
   */
  fun isDue(expression: String, since: Instant, now: Instant): Boolean {
    val executionTime = ExecutionTime.forCron(parser.parse(expression))
    val next = executionTime.nextExecution(since.atZone(ZoneOffset.UTC)).orElse(null) ?: return false
    return !next.toInstant().isAfter(now)
  }
}
