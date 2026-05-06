package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.port.`in`.app.SmartDefaultPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Instant
import mu.KLogging
import javax.script.ScriptEngineManager

@ApplicationScoped
@Suppress("Unused")
class SmartDefaultService : SmartDefaultPort {

  override fun computeSmartDefaults(entity: EntityDefinition, now: Instant): Map<String, String?> {
    val propertiesWithSmartDefaults = entity.properties.filter { it.smartDefault != null }
    if (propertiesWithSmartDefaults.isEmpty()) return emptyMap()

    val engine = ScriptEngineManager(Thread.currentThread().contextClassLoader).getEngineByExtension("kts") ?: run {
      logger.warn { "Kotlin scripting engine not available – smart defaults skipped" }
      return emptyMap()
    }

    val result = mutableMapOf<String, String?>()
    for (property in entity.properties) {
      val script = property.smartDefault ?: continue
      try {
        val bindings = engine.createBindings()
        bindings[BINDING_DATA] = result.toMap()
        bindings[BINDING_NOW] = now
        val value = engine.eval(buildWrappedScript(script), bindings)
        result[property.id.value] = value?.toString()
      } catch (e: Exception) {
        logger.warn { "Smart default evaluation failed for property ${property.id.value} (${property.name}): ${e.message}" }
        result[property.id.value] = null
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
