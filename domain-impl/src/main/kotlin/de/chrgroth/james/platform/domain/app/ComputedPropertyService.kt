package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.infra.ScriptType
import de.chrgroth.james.platform.domain.port.`in`.app.ComputedPropertyPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Instant
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.script.ScriptEngineManager

@ApplicationScoped
@Suppress("Unused")
class ComputedPropertyService(
  private val scriptMetrics: ScriptMetrics,
  @param:ConfigProperty(name = "app.script.timeout-ms", defaultValue = "500")
  private val scriptTimeoutMs: Long,
) : ComputedPropertyPort {

  private val scriptExecutor = Executors.newVirtualThreadPerTaskExecutor()

  // Lazily initialised on first use so the ServiceLoader classpath scan runs once per JVM instance
  // rather than on every request. Thread.currentThread().contextClassLoader is required in Quarkus
  // to pick up the JSR 223 Kotlin scripting engine via ServiceLoader.
  private val scriptEngineManager: ScriptEngineManager by lazy {
    ScriptEngineManager(Thread.currentThread().contextClassLoader)
  }

  // Lazily initialised engine instance so the expensive engine creation only happens once per
  // application lifecycle rather than on every computeValues call.
  private val scriptEngine: javax.script.ScriptEngine? by lazy {
    scriptEngineManager.getEngineByExtension("kts").also {
      if (it == null) logger.warn { "Kotlin scripting engine not available – computed properties will be skipped" }
    }
  }

  override fun computeValues(entity: EntityDefinition, data: Map<String, String?>, now: Instant): Map<String, String?> {
    val propertiesWithScript = entity.computedProperties.filter { it.script != null }
    if (propertiesWithScript.isEmpty()) return emptyMap()

    val engine = scriptEngine ?: run {
      logger.warn { "Kotlin scripting engine not available – computed properties skipped" }
      return emptyMap()
    }

    val result = mutableMapOf<String, String?>()
    for (computedProperty in entity.computedProperties) {
      val script = computedProperty.script ?: continue
      val startNs = System.nanoTime()
      var success = true
      try {
        val bindings = engine.createBindings()
        bindings[BINDING_DATA] = data
        bindings[BINDING_COMPUTED] = result.toMap()
        bindings[BINDING_NOW] = now
        val future = scriptExecutor.submit(Callable { engine.eval(buildWrappedScript(script), bindings) })
        val value = future.get(scriptTimeoutMs, TimeUnit.MILLISECONDS)
        result[computedProperty.id.value] = value?.toString()
      } catch (e: TimeoutException) {
        success = false
        logger.warn { "Computed property evaluation timed out after ${scriptTimeoutMs}ms for ${computedProperty.id.value} (${computedProperty.name})" }
        result[computedProperty.id.value] = null
      } catch (e: ExecutionException) {
        success = false
        logger.warn { "Computed property evaluation failed for ${computedProperty.id.value} (${computedProperty.name}): ${e.cause?.message}" }
        result[computedProperty.id.value] = null
      } catch (e: Exception) {
        success = false
        logger.warn { "Computed property evaluation failed for ${computedProperty.id.value} (${computedProperty.name}): ${e.message}" }
        result[computedProperty.id.value] = null
      } finally {
        scriptMetrics.record(ScriptType.COMPUTED_PROPERTY, entity.name, computedProperty.name, (System.nanoTime() - startNs) / 1_000_000L, success)
      }
    }
    return result
  }

  private fun buildWrappedScript(script: String): String = buildString {
    appendLine("@file:OptIn(kotlin.time.ExperimentalTime::class)")
    appendLine("@file:Suppress(\"UNCHECKED_CAST\")")
    appendLine()
    appendLine("val it: Map<String, String?> = ($BINDING_DATA ?: emptyMap<String, String?>()) as Map<String, String?>")
    appendLine("val computed: Map<String, String?> = ($BINDING_COMPUTED ?: emptyMap<String, String?>()) as Map<String, String?>")
    appendLine("val now: kotlin.time.Instant = $BINDING_NOW as kotlin.time.Instant")
    appendLine()
    append(script)
  }

  companion object : KLogging() {
    private const val BINDING_DATA = "_computedData"
    private const val BINDING_COMPUTED = "_computedResults"
    private const val BINDING_NOW = "_computedNow"
  }
}
