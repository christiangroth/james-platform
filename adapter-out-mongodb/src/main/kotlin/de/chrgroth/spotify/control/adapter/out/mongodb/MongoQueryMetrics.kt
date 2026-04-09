package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.infra.MongoQueryStats
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit

@ApplicationScoped
class MongoQueryMetrics(
  private val meterRegistry: MeterRegistry,
  @param:ConfigProperty(name = "app.mongodb.slow-query-threshold-ms")
  private val slowQueryThresholdMs: Long,
) {

  private val timers = ConcurrentHashMap<String, Timer>()
  private val slowQueryCounters = ConcurrentHashMap<String, Counter>()
  private val executionTimestamps = ConcurrentHashMap<String, ConcurrentLinkedDeque<Instant>>()
  private val slowQueryTimestamps = ConcurrentHashMap<String, ConcurrentLinkedDeque<Instant>>()

  fun <T> timed(operation: String, block: () -> T): T {
    val startMs = System.currentTimeMillis()
    val result = block()
    val durationMs = System.currentTimeMillis() - startMs
    recordMetrics(operation, durationMs)
    return result
  }

  fun getQueryStats(): List<MongoQueryStats> {
    val cutoff = Instant.now().minusSeconds(WINDOW_SECONDS)
    val allOperations = executionTimestamps.keys.toSet()
    return allOperations
      .map { operation ->
        val execDeque = executionTimestamps[operation] ?: ConcurrentLinkedDeque()
        pruneOldEntries(execDeque)
        val slowDeque = slowQueryTimestamps[operation] ?: ConcurrentLinkedDeque()
        pruneOldEntries(slowDeque)
        MongoQueryStats(
          name = operation,
          executionCountLast24h = execDeque.count { it.isAfter(cutoff) }.toLong(),
          slowQueryCount = slowDeque.count { it.isAfter(cutoff) }.toLong(),
        )
      }
      .sortedBy { it.name }
  }

  private fun recordMetrics(operation: String, durationMs: Long) {
    timers.getOrPut(operation) {
      Timer.builder("mongodb.query")
        .tag("operation", operation)
        .register(meterRegistry)
    }.record(durationMs, TimeUnit.MILLISECONDS)

    val now = Instant.now()
    val execDeque = executionTimestamps.getOrPut(operation) { ConcurrentLinkedDeque() }
    execDeque.add(now)
    pruneOldEntries(execDeque)

    if (durationMs >= slowQueryThresholdMs) {
      logger.warn { "Slow MongoDB query detected: operation=$operation duration=${durationMs}ms threshold=${slowQueryThresholdMs}ms" }
      slowQueryCounters.getOrPut(operation) {
        meterRegistry.counter("mongodb.slow.queries", "operation", operation)
      }.increment()

      val slowDeque = slowQueryTimestamps.getOrPut(operation) { ConcurrentLinkedDeque() }
      slowDeque.add(now)
      pruneOldEntries(slowDeque)
    }
  }

  private fun pruneOldEntries(deque: ConcurrentLinkedDeque<Instant>) {
    val cutoff = Instant.now().minusSeconds(WINDOW_SECONDS)
    while (deque.peekFirst()?.isBefore(cutoff) == true) {
      deque.pollFirst()
    }
  }

  companion object : KLogging() {
    private const val WINDOW_SECONDS = 24L * 60L * 60L
  }
}
