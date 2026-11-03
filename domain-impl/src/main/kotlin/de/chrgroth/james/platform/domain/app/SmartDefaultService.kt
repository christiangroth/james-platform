package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.expression.EntityExpressionContext
import de.chrgroth.james.platform.domain.expression.EntityExpressions
import de.chrgroth.james.platform.domain.expression.Expression
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.port.`in`.app.SmartDefaultContext
import de.chrgroth.james.platform.domain.port.`in`.app.SmartDefaultPort
import jakarta.enterprise.context.ApplicationScoped

/**
 * Evaluates smart defaults with the expression language (ADR 0029). Expressions see the static defaults, the values entered so far and the smart
 * defaults of the properties before them (property order), `config.<field>`, `today()`/`now()` and the entity's latest existing object via
 * `last(field)`/`next_day(field)`. A missing, invalid or valueless expression yields null.
 */
@ApplicationScoped
@Suppress("Unused")
class SmartDefaultService : SmartDefaultPort {

  override fun computeSmartDefaults(entity: EntityDefinition, context: SmartDefaultContext): Map<String, String?> {
    val properties = entity.properties.filter { it.smartDefault != null }
    if (properties.isEmpty()) return emptyMap()
    val values = HashMap<String, String?>()
    entity.properties.forEach { property -> property.default?.let { values[property.id.value] = it } }
    context.formValues.forEach { (id, value) -> if (!value.isNullOrEmpty()) values[id] = value }
    val latest by lazy { EntityExpressions.latest(entity, context.entries()) }
    val expressionContext = EntityExpressionContext(
      properties = entity.properties,
      data = values,
      config = context.config,
      configEntity = context.configEntity,
      now = EntityExpressions.localNow(context.now),
      lastValue = { name -> latest?.let { item -> EntityExpressions.resolve(entity.properties, name)?.let { EntityExpressions.value(item.data, it) } } },
    )
    val result = LinkedHashMap<String, String?>()
    for (property in properties) {
      val expression = EntityExpressions.parseOrNull(property.smartDefault!!)
      val value = expression?.let { runCatching { it.evaluate(expressionContext) }.getOrNull() }
      val raw = EntityExpressions.store(value, property.type)
      result[property.id.value] = raw
      if (context.formValues[property.id.value].isNullOrEmpty() && raw != null) values[property.id.value] = raw
    }
    return result
  }

  override fun formDependencies(entity: EntityDefinition): Map<String, Set<String>> = entity.properties.mapNotNull { property ->
    val expression = property.smartDefault?.let { EntityExpressions.parseOrNull(it) } ?: return@mapNotNull null
    val sources = expression.directIdentifiers
      .filter { Expression.configPath(it) == null }
      .mapNotNullTo(LinkedHashSet()) { EntityExpressions.resolve(entity.properties, it)?.first()?.id?.value }
    sources.remove(property.id.value)
    if (sources.isEmpty()) null else property.id.value to sources
  }.toMap()
}
