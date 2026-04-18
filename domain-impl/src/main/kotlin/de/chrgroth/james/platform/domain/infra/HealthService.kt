package de.chrgroth.james.platform.domain.infra

import de.chrgroth.james.platform.domain.model.infra.HealthStats
import de.chrgroth.james.platform.domain.port.`in`.infra.HealthPort
import de.chrgroth.james.platform.domain.port.out.infra.ConfigurationInfoPort
import de.chrgroth.james.platform.domain.port.out.infra.CronjobInfoPort
import de.chrgroth.james.platform.domain.port.out.infra.MongoStatsPort
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext

@ApplicationScoped
@Suppress("Unused")
class HealthService(
  private val mongoStats: MongoStatsPort,
  private val cronjobInfo: CronjobInfoPort,
  private val configurationInfo: ConfigurationInfoPort,
) : HealthPort {

  override fun getStats(): HealthStats = runBlocking {
    val dispatcher = Dispatchers.IO + tcclContext()
    val mongoCollectionStatsAsync = async(dispatcher) { mongoStats.getCollectionStats() }
    val mongoQueryStatsAsync = async(dispatcher) { mongoStats.getQueryStats() }
    val cronjobStatsAsync = async(dispatcher) { cronjobInfo.getCronjobStats() }
    val configurationStatsAsync = async(dispatcher) { configurationInfo.getConfigurationStats() }
    HealthStats(
      mongoCollectionStats = mongoCollectionStatsAsync.await(),
      mongoQueryStats = mongoQueryStatsAsync.await(),
      cronjobStats = cronjobStatsAsync.await(),
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
