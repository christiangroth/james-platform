package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.infra.HttpResponseStats
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

interface ResponseTimingDetails {
  fun <T> detail(name: String, block: () -> T): T
}

@ApplicationScoped
@Suppress("Unused")
class HttpResponseMetrics(
  private val meterRegistry: MeterRegistry,
  @param:ConfigProperty(name = "app.web.slow-response-threshold-ms")
  private val slowResponseThresholdMs: Long,
) {

  private val timers = ConcurrentHashMap<String, Timer>()
  private val slowResponseCounters = ConcurrentHashMap<String, Counter>()
  private val executionTimestamps = ConcurrentHashMap<String, ConcurrentLinkedDeque<Instant>>()
  private val slowResponseTimestamps = ConcurrentHashMap<String, ConcurrentLinkedDeque<Instant>>()

  fun <T> timed(operation: String, block: (ResponseTimingDetails) -> T): T {
    val details = Details()
    val startMs = System.currentTimeMillis()
    val result = block(details)
    val durationMs = System.currentTimeMillis() - startMs
    recordMetrics(operation, durationMs, details)
    return result
  }

  fun getResponseStats(): List<HttpResponseStats> {
    val cutoff = Instant.now().minusSeconds(WINDOW_SECONDS)
    val allOperations = executionTimestamps.keys.toSet()
    return allOperations
      .map { operation ->
        val execDeque = executionTimestamps[operation] ?: ConcurrentLinkedDeque()
        pruneOldEntries(execDeque)
        val slowDeque = slowResponseTimestamps[operation] ?: ConcurrentLinkedDeque()
        pruneOldEntries(slowDeque)
        HttpResponseStats(
          name = operation,
          executionCountLast24h = execDeque.count { it.isAfter(cutoff) }.toLong(),
          slowResponseCount = slowDeque.count { it.isAfter(cutoff) }.toLong(),
        )
      }
      .sortedBy { it.name }
  }

  private fun recordMetrics(operation: String, durationMs: Long, details: Details) {
    timers.getOrPut(operation) {
      Timer.builder("http.response")
        .tag("operation", operation)
        .register(meterRegistry)
    }.record(durationMs, TimeUnit.MILLISECONDS)

    val now = Instant.now()
    val execDeque = executionTimestamps.getOrPut(operation) { ConcurrentLinkedDeque() }
    execDeque.add(now)
    pruneOldEntries(execDeque)

    if (durationMs >= slowResponseThresholdMs) {
      val detailsSuffix = if (details.entries.isNotEmpty()) {
        " details=[${details.entries.joinToString(", ") { "${it.first}=${it.second}ms" }}]"
      } else {
        ""
      }
      logger.warn { "Slow HTTP response detected: operation=$operation duration=${durationMs}ms threshold=${slowResponseThresholdMs}ms$detailsSuffix" }

      slowResponseCounters.getOrPut(operation) {
        meterRegistry.counter("http.response.slow", "operation", operation)
      }.increment()

      val slowDeque = slowResponseTimestamps.getOrPut(operation) { ConcurrentLinkedDeque() }
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

  private class Details : ResponseTimingDetails {
    val entries = mutableListOf<Pair<String, Long>>()

    override fun <T> detail(name: String, block: () -> T): T {
      val startMs = System.currentTimeMillis()
      val result = block()
      val durationMs = System.currentTimeMillis() - startMs
      entries.add(name to durationMs)
      return result
    }
  }

  companion object : KLogging() {
    private const val WINDOW_SECONDS = 24L * 60L * 60L
  }
}
