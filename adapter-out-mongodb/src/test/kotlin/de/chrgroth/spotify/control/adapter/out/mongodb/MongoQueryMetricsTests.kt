package de.chrgroth.spotify.control.adapter.out.mongodb

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MongoQueryMetricsTests {

  private val meterRegistry = SimpleMeterRegistry()
  private val metrics = MongoQueryMetrics(
    meterRegistry = meterRegistry,
    slowQueryThresholdMs = 100L,
  )

  @Test
  fun `timed returns block result`() {
    val result = metrics.timed("test.op") { "actual" }
    assertThat(result).isEqualTo("actual")
  }

  @Test
  fun `timed records execution count in getQueryStats`() {
    metrics.timed("test.success.stats") { 42L }

    val stats = metrics.getQueryStats()
    val stat = stats.find { it.name == "test.success.stats" }
    assertThat(stat).isNotNull()
    assertThat(stat!!.executionCountLast24h).isEqualTo(1L)
  }
}
