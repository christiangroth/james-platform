package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.model.infra.ImportCleanupStats
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.LongAdder
import kotlin.time.toKotlinInstant

@ApplicationScoped
class ImportCleanupMetrics(private val meterRegistry: MeterRegistry) {

  private val executionCount = LongAdder()
  private val deletedCount = LongAdder()
  private val errorCount = LongAdder()
  private val totalDurationMs = LongAdder()
  private val lastRunAt = AtomicReference<Instant?>(null)

  private val timer = Timer.builder("import.cleanup.execution").register(meterRegistry)
  private val deletedCounter = Counter.builder("import.cleanup.deleted").register(meterRegistry)
  private val errorCounter = Counter.builder("import.cleanup.errors").register(meterRegistry)

  fun record(durationMs: Long, deleted: Long, success: Boolean) {
    executionCount.increment()
    totalDurationMs.add(durationMs)
    lastRunAt.set(Instant.now())
    timer.record(durationMs, TimeUnit.MILLISECONDS)

    deletedCount.add(deleted)
    deletedCounter.increment(deleted.toDouble())

    if (!success) {
      errorCount.increment()
      errorCounter.increment()
    }
  }

  fun getStats(): ImportCleanupStats = ImportCleanupStats(
    executionCount = executionCount.sum(),
    deletedCount = deletedCount.sum(),
    errorCount = errorCount.sum(),
    totalDurationMs = totalDurationMs.sum(),
    lastRunAt = lastRunAt.get()?.toKotlinInstant(),
  )
}
