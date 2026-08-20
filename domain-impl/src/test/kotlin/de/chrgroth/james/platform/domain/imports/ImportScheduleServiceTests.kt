package de.chrgroth.james.platform.domain.imports

import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.imports.ImportConnectionId
import de.chrgroth.james.platform.domain.model.imports.ImportDefinition
import de.chrgroth.james.platform.domain.model.imports.ImportDefinitionId
import de.chrgroth.james.platform.domain.model.imports.ImportJob
import de.chrgroth.james.platform.domain.model.imports.ImportJobId
import de.chrgroth.james.platform.domain.model.imports.ImportStatus
import de.chrgroth.james.platform.domain.port.`in`.imports.ImportPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportDefinitionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.infra.NotificationPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ImportScheduleServiceTests {

  private val importDefinitionRepository = mockk<ImportDefinitionRepositoryPort>()
  private val importPort = mockk<ImportPort>()
  private val notificationPort = mockk<NotificationPort>(relaxed = true)
  private val service = ImportScheduleService(importDefinitionRepository, importPort, notificationPort)

  private fun definition(
    id: String,
    schedule: String?,
    lastRunAt: Instant?,
    createdAt: Instant = Instant.now().minusSeconds(86_400),
    notifyOnSlack: Boolean = false,
  ) = ImportDefinition(
    id = ImportDefinitionId(id),
    userId = "user-1",
    connectionId = ImportConnectionId("conn-1"),
    name = "Test Definition",
    targetEntityDefinitionId = EntityDefinitionId("entity-1"),
    schedule = schedule,
    lastRunAt = lastRunAt,
    notifyOnSlack = notifyOnSlack,
    createdAt = createdAt,
    lastChangedAt = createdAt,
  )

  private fun job(id: String) = ImportJob(
    id = ImportJobId(id),
    userId = "user-1",
    installedAppId = InstalledAppId("installed-1"),
    importDefinitionId = ImportDefinitionId("def-1"),
    status = ImportStatus.ACCEPTING,
    payload = "{}",
    createdAt = Instant.now(),
    lastChangedAt = Instant.now(),
  )

  @Test
  fun `triggers every due definition and returns the due count`() {
    val due = definition(id = "def-1", schedule = "0 0 3 * * ?", lastRunAt = Instant.parse("2026-01-01T02:00:00Z"))
    every { importDefinitionRepository.findAllWithScheduleSet() } returns listOf(due)
    every { importPort.triggerScheduledImport("def-1") } returns job("job-1").right()

    val result = service.runDueScheduledImports()

    assertThat(result).isEqualTo(1)
    verify(exactly = 1) { importPort.triggerScheduledImport("def-1") }
  }

  @Test
  fun `skips a definition whose cron expression is not yet due`() {
    val notDue = definition(id = "def-1", schedule = "0 0 3 * * ?", lastRunAt = Instant.now())
    every { importDefinitionRepository.findAllWithScheduleSet() } returns listOf(notDue)

    val result = service.runDueScheduledImports()

    assertThat(result).isEqualTo(0)
    verify(exactly = 0) { importPort.triggerScheduledImport(any()) }
  }

  @Test
  fun `skips a definition with an invalid cron expression instead of failing the whole poll`() {
    val invalid = definition(id = "def-1", schedule = "not a cron", lastRunAt = null)
    val due = definition(id = "def-2", schedule = "0 0 3 * * ?", lastRunAt = Instant.parse("2026-01-01T02:00:00Z"))
    every { importDefinitionRepository.findAllWithScheduleSet() } returns listOf(invalid, due)
    every { importPort.triggerScheduledImport("def-2") } returns job("job-1").right()

    val result = service.runDueScheduledImports()

    assertThat(result).isEqualTo(1)
    verify(exactly = 0) { importPort.triggerScheduledImport("def-1") }
    verify(exactly = 1) { importPort.triggerScheduledImport("def-2") }
  }

  @Test
  fun `continues polling even when triggering a due definition fails`() {
    val due = definition(id = "def-1", schedule = "0 0 3 * * ?", lastRunAt = Instant.parse("2026-01-01T02:00:00Z"))
    every { importDefinitionRepository.findAllWithScheduleSet() } returns listOf(due)
    every { importPort.triggerScheduledImport("def-1") } returns ImportError.SCHEMA_DRIFT_DETECTED.left()

    val result = service.runDueScheduledImports()

    assertThat(result).isEqualTo(1)
    verify(exactly = 1) { importPort.triggerScheduledImport("def-1") }
  }

  @Test
  fun `does not notify on a failed trigger when notifyOnSlack is unset`() {
    val due = definition(id = "def-1", schedule = "0 0 3 * * ?", lastRunAt = Instant.parse("2026-01-01T02:00:00Z"), notifyOnSlack = false)
    every { importDefinitionRepository.findAllWithScheduleSet() } returns listOf(due)
    every { importPort.triggerScheduledImport("def-1") } returns ImportError.SCHEMA_DRIFT_DETECTED.left()

    service.runDueScheduledImports()

    verify(exactly = 0) { notificationPort.notify(any()) }
  }

  @Test
  fun `sends an explicit warning on schema drift when notifyOnSlack is set`() {
    val due = definition(id = "def-1", schedule = "0 0 3 * * ?", lastRunAt = Instant.parse("2026-01-01T02:00:00Z"), notifyOnSlack = true)
    every { importDefinitionRepository.findAllWithScheduleSet() } returns listOf(due)
    every { importPort.triggerScheduledImport("def-1") } returns ImportError.SCHEMA_DRIFT_DETECTED.left()

    service.runDueScheduledImports()

    verify(exactly = 1) { notificationPort.notify(match { it.contains("Test Definition") && it.contains("schema") }) }
  }

  @Test
  fun `sends a generic failure notification for a non-schema-drift error when notifyOnSlack is set`() {
    val due = definition(id = "def-1", schedule = "0 0 3 * * ?", lastRunAt = Instant.parse("2026-01-01T02:00:00Z"), notifyOnSlack = true)
    every { importDefinitionRepository.findAllWithScheduleSet() } returns listOf(due)
    every { importPort.triggerScheduledImport("def-1") } returns ImportError.CONNECTION_NOT_FOUND.left()

    service.runDueScheduledImports()

    verify(exactly = 1) { notificationPort.notify(match { it.contains("Test Definition") && it.contains(ImportError.CONNECTION_NOT_FOUND.code) }) }
  }

  @Test
  fun `does not notify on a successfully triggered import - the accept outcome is reported later by ImportService_handle`() {
    val due = definition(id = "def-1", schedule = "0 0 3 * * ?", lastRunAt = Instant.parse("2026-01-01T02:00:00Z"), notifyOnSlack = true)
    every { importDefinitionRepository.findAllWithScheduleSet() } returns listOf(due)
    every { importPort.triggerScheduledImport("def-1") } returns job("job-1").right()

    service.runDueScheduledImports()

    verify(exactly = 0) { notificationPort.notify(any()) }
  }
}
