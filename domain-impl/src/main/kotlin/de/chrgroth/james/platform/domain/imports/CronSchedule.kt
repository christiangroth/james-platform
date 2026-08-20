package de.chrgroth.james.platform.domain.imports

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import java.time.Instant
import java.time.ZoneOffset

/**
 * Validates and evaluates the Quartz-style cron expressions stored in [de.chrgroth.james.platform.domain.model.imports.ImportDefinition.schedule]
 * - the same dialect already used for `@Scheduled(cron = ...)` elsewhere in this codebase (e.g. `ImportJobCleanupJob`'s
 * `app.imports.cleanup.cron`), so a user-facing cron string means the same thing everywhere in the app. Unlike
 * `@Scheduled`, these expressions are per-[de.chrgroth.james.platform.domain.model.imports.ImportDefinition] and
 * evaluated dynamically against a stored `lastRunAt`, so they cannot be registered as static `@Scheduled` triggers.
 */
object CronSchedule {

  private val parser = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ))

  fun isValid(expression: String): Boolean = try {
    parser.parse(expression).validate()
    true
  } catch (e: IllegalArgumentException) {
    false
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
