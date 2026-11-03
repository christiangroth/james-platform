package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.expression.EntityExpressionContext
import de.chrgroth.james.platform.domain.expression.EntityExpressions
import de.chrgroth.james.platform.domain.expression.ExpressionValue
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.port.`in`.app.ComputedPropertyPort
import de.chrgroth.james.platform.domain.report.ReportExpressions
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Instant

/**
 * Evaluates computed properties with the expression language (ADR 0029): pure Kotlin, no scripting engine, so it also works in the native image.
 * An expression that is missing, invalid (e.g. a field deleted after it was saved) or has no value yields null for that computed property.
 */
@ApplicationScoped
@Suppress("Unused")
class ComputedPropertyService : ComputedPropertyPort {

  override fun computeValues(
    entity: EntityDefinition,
    data: Map<String, String?>,
    now: Instant,
    config: Map<String, String?>,
    configEntity: EntityDefinition?,
  ): Map<String, String?> {
    if (entity.computedProperties.none { it.expression != null }) return emptyMap()
    val result = LinkedHashMap<String, String?>()
    val context = EntityExpressionContext(entity.properties, data, config, configEntity, EntityExpressions.localNow(now), extraField = { name ->
      // a computed property evaluated before this one, by name
      entity.computedProperties.find { it.name == name && result.containsKey(it.id.value) }?.let { ExpressionValue.of(result[it.id.value], ReportExpressions.typeOf(it.type)) }
    })
    for (computed in entity.computedProperties) {
      val expression = computed.expression?.let { EntityExpressions.parseOrNull(it) }
      val value = expression?.let { runCatching { it.evaluate(context) }.getOrNull() }
      result[computed.id.value] = EntityExpressions.store(value, computed.type)
    }
    return result
  }
}
