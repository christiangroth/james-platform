package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.infra.HealthStats
import de.chrgroth.spotify.control.domain.model.infra.PredicateStats
import de.chrgroth.spotify.control.domain.port.`in`.infra.HealthPort
import de.chrgroth.spotify.control.domain.port.out.infra.ConfigurationInfoPort
import de.chrgroth.spotify.control.domain.port.out.infra.CronjobInfoPort
import de.chrgroth.spotify.control.domain.port.out.infra.MongoStatsPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutgoingRequestStatsPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackActivityPort
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext

@ApplicationScoped
@Suppress("Unused")
class HealthService(
  private val outboxPort: OutboxPort,
  private val outgoingRequestStats: OutgoingRequestStatsPort,
  private val mongoStats: MongoStatsPort,
  private val cronjobInfo: CronjobInfoPort,
  private val configurationInfo: ConfigurationInfoPort,
  private val playbackActivity: PlaybackActivityPort,
) : HealthPort {

  override fun getStats(): HealthStats = runBlocking {
    val dispatcher = Dispatchers.IO + tcclContext()
    val outgoingRequestStatsAsync = async(dispatcher) { outgoingRequestStats.getRequestStats() }
    val outboxPartitionsAsync = async(dispatcher) { outboxPort.getPartitionStats() }
    val mongoCollectionStatsAsync = async(dispatcher) { mongoStats.getCollectionStats() }
    val mongoQueryStatsAsync = async(dispatcher) { mongoStats.getQueryStats() }
    val cronjobStatsAsync = async(dispatcher) { cronjobInfo.getCronjobStats() }
    val playbackActiveAsync = async(dispatcher) { playbackActivity.isPlaybackActive() }
    val lastActivityTimestampAsync = async(dispatcher) { playbackActivity.lastActivityTimestamp() }
    val configurationStatsAsync = async(dispatcher) { configurationInfo.getConfigurationStats() }
    HealthStats(
      outgoingRequestStats = outgoingRequestStatsAsync.await(),
      outboxPartitions = outboxPartitionsAsync.await(),
      mongoCollectionStats = mongoCollectionStatsAsync.await(),
      mongoQueryStats = mongoQueryStatsAsync.await(),
      cronjobStats = cronjobStatsAsync.await(),
      predicateStats = listOf(
        PredicateStats(name = "playbackActive", active = playbackActiveAsync.await(), lastCheck = lastActivityTimestampAsync.await()),
      ),
      configurationStats = configurationStatsAsync.await(),
    )
  }
}

/**
 * Quarkus uses a custom classloader per-application. When coroutines switch threads via Dispatchers.IO
 * the new thread's context classloader may still point to the system classloader, which causes CDI,
 * JNDI, and other framework lookups to fail at runtime. [TcclContext] propagates the calling thread's
 * context classloader into each coroutine thread so that all framework lookups continue to work.
 */
private class TcclContext(private val classLoader: ClassLoader) : ThreadContextElement<ClassLoader?> {
  companion object Key : CoroutineContext.Key<TcclContext>
  override val key: CoroutineContext.Key<*> = Key
  override fun updateThreadContext(context: CoroutineContext): ClassLoader? =
    Thread.currentThread().contextClassLoader.also { Thread.currentThread().contextClassLoader = classLoader }
  override fun restoreThreadContext(context: CoroutineContext, oldState: ClassLoader?) {
    Thread.currentThread().contextClassLoader = oldState
  }
}

private fun tcclContext(): TcclContext = TcclContext(Thread.currentThread().contextClassLoader)
