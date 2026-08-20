package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.model.imports.ImportDefinition
import de.chrgroth.james.platform.domain.port.`in`.imports.ImportPort
import de.chrgroth.james.platform.domain.port.`in`.imports.ImportSchedulePort
import de.chrgroth.james.platform.domain.port.out.imports.ImportDefinitionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.infra.NotificationPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.Instant

@ApplicationScoped
@Suppress("Unused")
class ImportScheduleService(
  private val importDefinitionRepository: ImportDefinitionRepositoryPort,
  private val importPort: ImportPort,
  private val notificationPort: NotificationPort,
) : ImportSchedulePort {

  override fun runDueScheduledImports(): Int {
    val now = Instant.now()
    val due = importDefinitionRepository.findAllWithScheduleSet().filter { definition ->
      val schedule = definition.schedule ?: return@filter false
      CronSchedule.isValid(schedule) && CronSchedule.isDue(schedule, definition.lastRunAt ?: definition.createdAt, now)
    }

    due.forEach { definition ->
      importPort.triggerScheduledImport(definition.id.value).fold(
        { error ->
          logger.warn { "Scheduled import failed: definitionId=${definition.id.value} error=${error.code}" }
          if (definition.notifyOnSlack) {
            notificationPort.notify(failureMessage(definition, error))
          }
        },
        { job -> logger.info { "Scheduled import triggered: definitionId=${definition.id.value} importJobId=${job.id.value}" } },
      )
    }
    if (due.isNotEmpty()) {
      logger.info { "Scheduled import poll: due=${due.size}" }
    }
    return due.size
  }

  /**
   * The run failed before an [de.chrgroth.james.platform.domain.model.imports.ImportJob] could even be queued for
   * accept (see `ImportPort.triggerScheduledImport`), so this is the only place able to report it - a later success
   * or failure of the actual accept is instead reported from `ImportService.handle` once known, since accept runs
   * asynchronously via the outbox.
   */
  private fun failureMessage(definition: ImportDefinition, error: DomainError): String =
    if (error == ImportError.SCHEMA_DRIFT_DETECTED) {
      "Scheduled import \"${definition.name}\" aborted: the detected schema no longer matches the last accepted run. Please review the definition's filter/mapping."
    } else {
      "Scheduled import \"${definition.name}\" failed: ${error.code}."
    }

  companion object : KLogging()
}
