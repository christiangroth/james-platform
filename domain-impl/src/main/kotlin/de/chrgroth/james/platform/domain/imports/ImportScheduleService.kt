package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.port.`in`.imports.ImportPort
import de.chrgroth.james.platform.domain.port.`in`.imports.ImportSchedulePort
import de.chrgroth.james.platform.domain.port.out.imports.ImportDefinitionRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.Instant

@ApplicationScoped
@Suppress("Unused")
class ImportScheduleService(
  private val importDefinitionRepository: ImportDefinitionRepositoryPort,
  private val importPort: ImportPort,
) : ImportSchedulePort {

  override fun runDueScheduledImports(): Int {
    val now = Instant.now()
    val due = importDefinitionRepository.findAllWithScheduleSet().filter { definition ->
      val schedule = definition.schedule ?: return@filter false
      CronSchedule.isValid(schedule) && CronSchedule.isDue(schedule, definition.lastRunAt ?: definition.createdAt, now)
    }

    due.forEach { definition ->
      importPort.triggerScheduledImport(definition.id.value).fold(
        { error -> logger.warn { "Scheduled import failed: definitionId=${definition.id.value} error=${error.code}" } },
        { job -> logger.info { "Scheduled import triggered: definitionId=${definition.id.value} importJobId=${job.id.value}" } },
      )
    }
    if (due.isNotEmpty()) {
      logger.info { "Scheduled import poll: due=${due.size}" }
    }
    return due.size
  }

  companion object : KLogging()
}
