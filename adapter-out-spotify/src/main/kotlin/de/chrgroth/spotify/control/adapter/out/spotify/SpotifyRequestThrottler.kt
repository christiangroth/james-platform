package de.chrgroth.spotify.control.adapter.out.spotify

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.infra.SpotifyThrottlingPort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@ApplicationScoped
class SpotifyRequestThrottler(
  @param:ConfigProperty(name = "spotify.throttle.default-interval-ms")
  private val defaultThrottleIntervalMs: Long,
) : SpotifyThrottlingPort {

  private val lastRequestTimeByPartition = ConcurrentHashMap<String, Long>()
  private val throttleIntervalMs = AtomicLong(defaultThrottleIntervalMs)

  fun throttle(partitionKey: String) {
    if (partitionKey != DomainOutboxPartition.ToSpotify.key) return
    val lastTime = lastRequestTimeByPartition[partitionKey] ?: 0L
    val elapsed = System.currentTimeMillis() - lastTime
    val remaining = throttleIntervalMs.get() - elapsed
    if (remaining > 0) {
      Thread.sleep(remaining)
    }
    lastRequestTimeByPartition[partitionKey] = System.currentTimeMillis()
  }

  override fun getDefaultThrottleIntervalMs(): Long = defaultThrottleIntervalMs

  override fun getThrottleIntervalMs(): Long = throttleIntervalMs.get()

  override fun setThrottleIntervalMs(ms: Long) {
    throttleIntervalMs.set(ms)
  }
}
