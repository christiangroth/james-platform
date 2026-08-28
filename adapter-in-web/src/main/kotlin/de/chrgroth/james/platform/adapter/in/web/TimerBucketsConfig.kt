package de.chrgroth.james.platform.adapter.`in`.web

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import java.time.Duration

/**
 * Replaces Micrometer's default 1ms-30s / 69-bucket exponential histogram with small, hand-picked,
 * coarse bucket boundaries per metric. Applies globally regardless of how the meter was registered
 * (Timer.builder or @Timed), and enables histogram buckets even for meters that don't currently call
 * .publishPercentileHistogram() at all (e.g. this repo's own mongodb.query / http.response timers).
 */
@ApplicationScoped
class TimerBucketsConfig {

  @Produces
  fun timerBucketsFilter(): MeterFilter = object : MeterFilter {
    override fun configure(id: Meter.Id, config: DistributionStatisticConfig): DistributionStatisticConfig {
      val slos = SLOS[id.name] ?: return config
      return DistributionStatisticConfig.builder()
        .percentilesHistogram(true)
        .serviceLevelObjectives(*slos)
        .build()
        .merge(config)
    }
  }

  companion object {
    private val STANDARD_SLOS = millis(20, 50, 100, 200, 500, 1000, 2000, 5000, 10000)
    private val SCHEDULER_SLOS = millis(100, 250, 500, 1000, 2000, 5000, 10000, 30000, 60000)

    private val SLOS = mapOf(
      "mongodb.query" to STANDARD_SLOS,
      "http.response" to STANDARD_SLOS,
      "spotify.request" to STANDARD_SLOS,
      "scheduler.job" to SCHEDULER_SLOS
    )

    private fun millis(vararg values: Long): DoubleArray = values.map { Duration.ofMillis(it).toNanos().toDouble() }.toDoubleArray()
  }
}
