package de.chrgroth.spotify.control.adapter.out.spotify

import de.chrgroth.spotify.control.domain.model.infra.OutgoingRequestStats
import de.chrgroth.spotify.control.domain.port.out.infra.OutgoingRequestStatsObserver
import de.chrgroth.spotify.control.domain.port.out.infra.OutgoingRequestStatsPort
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance
import java.net.http.HttpResponse
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit

@ApplicationScoped
class SpotifyHttpMetrics(
  private val meterRegistry: MeterRegistry,
  @param:Any private val requestStatsObservers: Instance<OutgoingRequestStatsObserver>,
) : OutgoingRequestStatsPort {

  private val timers = ConcurrentHashMap<String, Timer>()
  private val requestTimestamps = ConcurrentHashMap<String, ConcurrentLinkedDeque<Instant>>()

  @PostConstruct
  fun initialize() {
    Gauge.builder("spotify.request.endpoints", requestTimestamps) { it.size.toDouble() }
      .description("Number of tracked Spotify API endpoints with recorded requests")
      .register(meterRegistry)
  }

  fun <T> timed(urlTemplate: String, block: () -> HttpResponse<T>): HttpResponse<T> {
    val startMs = System.currentTimeMillis()
    val response = block()
    val durationMs = System.currentTimeMillis() - startMs
    record(urlTemplate, response.statusCode(), durationMs)
    return response
  }

  private fun record(urlTemplate: String, statusCode: Int, durationMs: Long) {
    timers.getOrPut("${urlTemplate}_${statusCode}") {
      Timer.builder("spotify.request")
        .tag("url", urlTemplate)
        .tag("status", statusCode.toString())
        .register(meterRegistry)
    }.record(durationMs, TimeUnit.MILLISECONDS)

    val deque = requestTimestamps.getOrPut(urlTemplate) { ConcurrentLinkedDeque() }
    deque.add(Instant.now())
    pruneOldEntries(deque)
    requestStatsObservers.forEach { it.onRequestRecorded() }
  }

  override fun getRequestStats(): List<OutgoingRequestStats> {
    val cutoff = Instant.now().minusSeconds(WINDOW_SECONDS)
    return requestTimestamps.entries
      .map { (endpoint, deque) ->
        pruneOldEntries(deque)
        OutgoingRequestStats(
          endpoint = endpoint.let { if (it.contains('/')) it.substring(it.indexOf('/')) else it },
          requestCountLast24h = deque.count { it.isAfter(cutoff) }.toLong(),
        )
      }
      .sortedBy { it.endpoint }
  }

  private fun pruneOldEntries(deque: ConcurrentLinkedDeque<Instant>) {
    val cutoff = Instant.now().minusSeconds(WINDOW_SECONDS)
    while (deque.peekFirst()?.isBefore(cutoff) == true) {
      deque.pollFirst()
    }
  }

  companion object {
    private const val WINDOW_SECONDS = 24L * 60L * 60L
  }
}
