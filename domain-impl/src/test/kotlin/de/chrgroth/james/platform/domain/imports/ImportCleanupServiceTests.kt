package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.port.out.imports.ImportJobRepositoryPort
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.assertj.core.data.TemporalUnitWithinOffset
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class ImportCleanupServiceTests {

  private val importJobRepository = mockk<ImportJobRepositoryPort>()
  private val importCleanupMetrics = ImportCleanupMetrics(SimpleMeterRegistry())

  private val service = ImportCleanupService(importJobRepository, importCleanupMetrics, retentionDays = 14L)

  @Test
  fun `cleanup deletes jobs older than the configured retention period`() {
    val cutoffSlot = slot<Instant>()
    every { importJobRepository.deleteAllLastChangedBefore(capture(cutoffSlot)) } returns 3L

    val deleted = service.cleanupStaleImportJobs()

    assertThat(deleted).isEqualTo(3)
    val expectedCutoff = Instant.now().minus(14, ChronoUnit.DAYS)
    assertThat(cutoffSlot.captured).isCloseTo(expectedCutoff, TemporalUnitWithinOffset(5, ChronoUnit.SECONDS))
  }

  @Test
  fun `cleanup records metrics on success`() {
    every { importJobRepository.deleteAllLastChangedBefore(any()) } returns 7L

    service.cleanupStaleImportJobs()

    val stats = importCleanupMetrics.getStats()
    assertThat(stats.executionCount).isEqualTo(1L)
    assertThat(stats.deletedCount).isEqualTo(7L)
    assertThat(stats.errorCount).isEqualTo(0L)
  }

  @Test
  fun `cleanup records error metrics and rethrows when repository fails`() {
    every { importJobRepository.deleteAllLastChangedBefore(any()) } throws IllegalStateException("boom")

    assertThat(catchThrowable { service.cleanupStaleImportJobs() }).isInstanceOf(IllegalStateException::class.java)

    val stats = importCleanupMetrics.getStats()
    assertThat(stats.executionCount).isEqualTo(1L)
    assertThat(stats.errorCount).isEqualTo(1L)
    assertThat(stats.deletedCount).isEqualTo(0L)
  }
}
