package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.infra.ScriptExecutionStats
import de.chrgroth.james.platform.domain.model.infra.ScriptType
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder

@ApplicationScoped
class ScriptMetrics(private val meterRegistry: MeterRegistry) {

  private data class Key(val type: ScriptType, val entityName: String, val propertyName: String)

  private val timers = ConcurrentHashMap<Key, Timer>()
  private val executionCounts = ConcurrentHashMap<Key, LongAdder>()
  private val errorCounts = ConcurrentHashMap<Key, LongAdder>()
  private val totalDurationsMs = ConcurrentHashMap<Key, LongAdder>()

  fun record(type: ScriptType, entityName: String, propertyName: String, durationMs: Long, success: Boolean) {
    val key = Key(type, entityName, propertyName)

    timers.getOrPut(key) {
      Timer.builder("script.execution")
        .tag("type", type.name.lowercase())
        .tag("entity", entityName)
        .tag("property", propertyName)
        .register(meterRegistry)
    }.record(durationMs, TimeUnit.MILLISECONDS)

    executionCounts.getOrPut(key) { LongAdder() }.increment()
    totalDurationsMs.getOrPut(key) { LongAdder() }.add(durationMs)

    if (!success) {
      errorCounts.getOrPut(key) { LongAdder() }.increment()
    }
  }

  fun getStats(): List<ScriptExecutionStats> =
    executionCounts.keys
      .map { key ->
        ScriptExecutionStats(
          type = key.type,
          entityName = key.entityName,
          propertyName = key.propertyName,
          executionCount = executionCounts[key]?.sum() ?: 0L,
          errorCount = errorCounts[key]?.sum() ?: 0L,
          totalDurationMs = totalDurationsMs[key]?.sum() ?: 0L,
        )
      }
      .sortedWith(compareBy({ it.type.name }, { it.entityName }, { it.propertyName }))
}
