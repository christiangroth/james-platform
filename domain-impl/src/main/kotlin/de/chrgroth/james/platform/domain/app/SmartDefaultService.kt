package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.infra.ScriptType
import de.chrgroth.james.platform.domain.port.`in`.app.SmartDefaultPort
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
class SmartDefaultService(
  private val scriptMetrics: ScriptMetrics,
  @param:ConfigProperty(name = "app.script.timeout-ms", defaultValue = "500")
  private val scriptTimeoutMs: Long,
) : SmartDefaultPort {

  private val scriptExecutor = Executors.newVirtualThreadPerTaskExecutor()

  // Lazily initialised on first use so the ServiceLoader classpath scan runs once per JVM instance
  // rather than on every request. Thread.currentThread().contextClassLoader is required in Quarkus
  // to pick up the JSR 223 Kotlin scripting engine via ServiceLoader.
  private val scriptEngineManager: ScriptEngineManager by lazy {
    ScriptEngineManager(Thread.currentThread().contextClassLoader)
  }

  // Lazily initialised engine instance so the expensive engine creation only happens once per
  // application lifecycle rather than on every computeSmartDefaults call.
  private val scriptEngine: javax.script.ScriptEngine? by lazy {
    scriptEngineManager.getEngineByExtension("kts").also {
      if (it == null) logger.warn { "Kotlin scripting engine not available – smart defaults will be skipped" }
    }
  }

  override fun computeSmartDefaults(entity: EntityDefinition, now: Instant): Map<String, String?> {
    val propertiesWithSmartDefaults = entity.properties.filter { it.smartDefault != null }
    if (propertiesWithSmartDefaults.isEmpty()) return emptyMap()

    val engine = scriptEngine ?: run {
      logger.warn { "Kotlin scripting engine not available – smart defaults skipped" }
      return emptyMap()
    }

    val result = mutableMapOf<String, String?>()
    // Seed the `it` binding with all static defaults so smart default scripts can reference them
    val itSeed: MutableMap<String, String?> = entity.properties
      .filter { it.default != null }
      .associateTo(mutableMapOf()) { it.id.value to it.default }
    for (property in entity.properties) {
      val script = property.smartDefault ?: continue
      val startNs = System.nanoTime()
      var success = true
      try {
        val bindings = engine.createBindings()
        bindings[BINDING_DATA] = itSeed.toMap()
        bindings[BINDING_NOW] = now
        val future = scriptExecutor.submit(Callable { engine.eval(buildWrappedScript(script), bindings) })
        val value = future.get(scriptTimeoutMs, TimeUnit.MILLISECONDS)
        val computed = value?.toString()
        result[property.id.value] = computed
        itSeed[property.id.value] = computed
      } catch (e: TimeoutException) {
        success = false
        logger.warn { "Smart default evaluation timed out after ${scriptTimeoutMs}ms for property ${property.id.value} (${property.name})" }
        result[property.id.value] = null
      } catch (e: ExecutionException) {
        success = false
        logger.warn { "Smart default evaluation failed for property ${property.id.value} (${property.name}): ${e.cause?.message}" }
        result[property.id.value] = null
      } catch (e: Exception) {
        success = false
        logger.warn { "Smart default evaluation failed for property ${property.id.value} (${property.name}): ${e.message}" }
        result[property.id.value] = null
      } finally {
        scriptMetrics.record(ScriptType.SMART_DEFAULT, entity.name, property.name, (System.nanoTime() - startNs) / 1_000_000L, success)
      }
    }
    return result
  }

  private fun buildWrappedScript(script: String): String = buildString {
    appendLine("@file:OptIn(kotlin.time.ExperimentalTime::class)")
    appendLine("@file:Suppress(\"UNCHECKED_CAST\")")
    appendLine()
    appendLine("val it: Map<String, String?> = ($BINDING_DATA ?: emptyMap<String, String?>()) as Map<String, String?>")
    appendLine("val now: kotlin.time.Instant = $BINDING_NOW as kotlin.time.Instant")
    appendLine()
    append(script)
  }

  companion object : KLogging() {
    private const val BINDING_DATA = "_smartDefaultData"
    private const val BINDING_NOW = "_smartDefaultNow"
  }
}
