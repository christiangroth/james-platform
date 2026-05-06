package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.infra.ScriptType
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScriptMetricsTests {

  private val meterRegistry = SimpleMeterRegistry()
  private val metrics = ScriptMetrics(meterRegistry)

  @Test
  fun `getStats returns empty list when no scripts have been recorded`() {
    assertThat(metrics.getStats()).isEmpty()
  }

  @Test
  fun `record increments execution count`() {
    metrics.record(ScriptType.SMART_DEFAULT, "Order", "CreatedAt", 10L, true)

    val stats = metrics.getStats()
    assertThat(stats).hasSize(1)
    assertThat(stats[0].executionCount).isEqualTo(1L)
    assertThat(stats[0].errorCount).isEqualTo(0L)
  }

  @Test
  fun `record tracks error count separately`() {
    metrics.record(ScriptType.SMART_DEFAULT, "Order", "CreatedAt", 5L, true)
    metrics.record(ScriptType.SMART_DEFAULT, "Order", "CreatedAt", 8L, false)

    val stats = metrics.getStats()
    val stat = stats.single()
    assertThat(stat.executionCount).isEqualTo(2L)
    assertThat(stat.errorCount).isEqualTo(1L)
  }

  @Test
  fun `record accumulates total duration`() {
    metrics.record(ScriptType.COMPUTED_PROPERTY, "Product", "Price", 20L, true)
    metrics.record(ScriptType.COMPUTED_PROPERTY, "Product", "Price", 30L, true)

    val stats = metrics.getStats()
    val stat = stats.single()
    assertThat(stat.totalDurationMs).isEqualTo(50L)
  }

  @Test
  fun `record keeps separate stats per entity and property`() {
    metrics.record(ScriptType.SMART_DEFAULT, "Order", "Status", 5L, true)
    metrics.record(ScriptType.SMART_DEFAULT, "Order", "CreatedAt", 10L, true)

    val stats = metrics.getStats()
    assertThat(stats).hasSize(2)
    assertThat(stats.map { it.propertyName }).containsExactlyInAnyOrder("Status", "CreatedAt")
  }

  @Test
  fun `record keeps separate stats per script type`() {
    metrics.record(ScriptType.SMART_DEFAULT, "Order", "Status", 5L, true)
    metrics.record(ScriptType.COMPUTED_PROPERTY, "Order", "Status", 10L, true)

    val stats = metrics.getStats()
    assertThat(stats).hasSize(2)
    assertThat(stats.map { it.type }).containsExactlyInAnyOrder(ScriptType.SMART_DEFAULT, ScriptType.COMPUTED_PROPERTY)
  }

  @Test
  fun `getStats returns entries sorted by type then entity then property`() {
    metrics.record(ScriptType.SMART_DEFAULT, "Order", "Status", 5L, true)
    metrics.record(ScriptType.COMPUTED_PROPERTY, "Order", "Total", 5L, true)
    metrics.record(ScriptType.COMPUTED_PROPERTY, "Article", "Price", 5L, true)

    val stats = metrics.getStats()
    assertThat(stats.map { "${it.type}/${it.entityName}/${it.propertyName}" })
      .containsExactly(
        "COMPUTED_PROPERTY/Article/Price",
        "COMPUTED_PROPERTY/Order/Total",
        "SMART_DEFAULT/Order/Status",
      )
  }

  @Test
  fun `record registers timer metric in registry`() {
    metrics.record(ScriptType.SMART_DEFAULT, "Order", "CreatedAt", 15L, true)

    val timer = meterRegistry.find("script.execution")
      .tag("type", "smart_default")
      .tag("entity", "Order")
      .tag("property", "CreatedAt")
      .timer()
    assertThat(timer).isNotNull
    assertThat(timer!!.count()).isEqualTo(1L)
  }
}
