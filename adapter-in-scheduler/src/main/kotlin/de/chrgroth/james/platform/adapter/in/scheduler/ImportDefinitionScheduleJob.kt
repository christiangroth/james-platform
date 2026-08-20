package de.chrgroth.james.platform.adapter.`in`.scheduler

import de.chrgroth.james.platform.domain.port.`in`.imports.ImportSchedulePort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class ImportDefinitionScheduleJob(
  private val importSchedule: ImportSchedulePort,
) {

  @Scheduled(cron = "{app.imports.schedule.poll-cron}")
  fun runDueScheduledImports() {
    importSchedule.runDueScheduledImports()
  }
}
