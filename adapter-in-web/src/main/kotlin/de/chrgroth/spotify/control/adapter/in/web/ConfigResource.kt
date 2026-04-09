package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.port.`in`.user.RuntimeConfigPort
import de.chrgroth.spotify.control.domain.port.out.infra.ConfigurationInfoPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext

@Path("/config")
@ApplicationScoped
@Suppress("Unused")
class ConfigResource {

  @Inject
  @Location("config.html")
  private lateinit var configTemplate: Template

  @Inject
  private lateinit var configurationInfo: ConfigurationInfoPort

  @Inject
  private lateinit var runtimeConfig: RuntimeConfigPort

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun config(): TemplateInstance = runBlocking {
    val dispatcher = Dispatchers.IO + tcclContext()
    val statsAsync = async(dispatcher) { configurationInfo.getConfigurationStats() }
    val runtimeConfigAsync = async(dispatcher) { runtimeConfig.getRuntimeConfig() }
    configTemplate
      .data("stats", statsAsync.await())
      .data("runtimeConfig", runtimeConfigAsync.await())
  }
}

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
