package de.chrgroth.james.platform.domain.imports

import com.cronutils.model.Cron
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Validates and evaluates the Quartz-style cron expressions stored in [de.chrgroth.james.platform.domain.model.imports.ImportDefinition.schedule]
 * - the same dialect already used for `@Scheduled(cron = ...)` elsewhere in this codebase (e.g. `ImportJobCleanupJob`'s
 * `app.imports.cleanup.cron`), so a user-facing cron string means the same thing everywhere in the app. Unlike
 * `@Scheduled`, these expressions are per-[de.chrgroth.james.platform.domain.model.imports.ImportDefinition] and
 * evaluated dynamically (via [nextFireTime]) to compute each definition's next delayed-dispatch outbox event (see
 * `ImportService.rescheduleNextRun`), so they cannot be registered as static `@Scheduled` triggers.
 */
object CronSchedule {

  /** A sane lower bound on how often a single definition may schedule itself, independent of delayed-dispatch precision - protects against a typo'd cron expression (e.g. every second) flooding the outbox. */
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
   * [expression]'s next occurrence strictly after [after] - used both for a purely informational "next run" display
   * (e.g. the Import-Definitionen table) and to compute the `notBefore` instant for the delayed-dispatch
   * `DomainOutboxEvent.RunScheduledImport` outbox event enqueued by `ImportService.rescheduleNextRun` (see ADR 0019).
   * Null if [expression] is invalid or has no future occurrence.
   *
   * The expression is evaluated in [zone], by default the JVM default zone - the very zone the web adapter's
   * `.formatted` template extension renders instants in - so a user picking "daily at 06:30" gets 06:30 on the clock
   * they see in the UI, including across DST changes. (The minimum-interval check in [isValid] is zone-independent
   * and stays on UTC.)
   */
  fun nextFireTime(expression: String, after: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant? = try {
    ExecutionTime.forCron(parser.parse(expression)).nextExecution(after.atZone(zone)).orElse(null)?.toInstant()
  } catch (e: IllegalArgumentException) {
    null
  }
}
