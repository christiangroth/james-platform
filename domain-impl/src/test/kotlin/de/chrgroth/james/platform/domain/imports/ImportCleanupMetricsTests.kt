package de.chrgroth.james.platform.domain.imports

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ImportCleanupMetricsTests {

  private val meterRegistry = SimpleMeterRegistry()
  private val metrics = ImportCleanupMetrics(meterRegistry)

  @Test
  fun `getStats returns zeroed stats and no last run when nothing has been recorded`() {
    val stats = metrics.getStats()

    assertThat(stats.executionCount).isEqualTo(0L)
    assertThat(stats.deletedCount).isEqualTo(0L)
    assertThat(stats.errorCount).isEqualTo(0L)
    assertThat(stats.totalDurationMs).isEqualTo(0L)
    assertThat(stats.lastRunAt).isNull()
  }

  @Test
  fun `record accumulates execution count, deleted count and duration`() {
    metrics.record(durationMs = 10L, deleted = 3L, success = true)
    metrics.record(durationMs = 20L, deleted = 2L, success = true)

    val stats = metrics.getStats()
    assertThat(stats.executionCount).isEqualTo(2L)
    assertThat(stats.deletedCount).isEqualTo(5L)
    assertThat(stats.totalDurationMs).isEqualTo(30L)
    assertThat(stats.errorCount).isEqualTo(0L)
    assertThat(stats.lastRunAt).isNotNull()
  }

  @Test
  fun `record tracks error count separately from execution count`() {
    metrics.record(durationMs = 10L, deleted = 0L, success = false)

    val stats = metrics.getStats()
    assertThat(stats.executionCount).isEqualTo(1L)
    assertThat(stats.errorCount).isEqualTo(1L)
  }

  @Test
  fun `record registers timer and counter metrics in registry`() {
    metrics.record(durationMs = 15L, deleted = 4L, success = true)

    val timer = meterRegistry.find("import.cleanup.execution").timer()
    assertThat(timer).isNotNull
    assertThat(timer!!.count()).isEqualTo(1L)

    val deletedCounter = meterRegistry.find("import.cleanup.deleted").counter()
    assertThat(deletedCounter).isNotNull
    assertThat(deletedCounter!!.count()).isEqualTo(4.0)
  }
}
