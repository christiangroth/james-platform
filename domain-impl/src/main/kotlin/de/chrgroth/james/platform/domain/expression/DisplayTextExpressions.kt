package de.chrgroth.james.platform.domain.expression

import de.chrgroth.james.platform.domain.app.ComputedPropertyService
import de.chrgroth.james.platform.domain.error.ExpressionErrorCode
import de.chrgroth.james.platform.domain.error.InvalidExpressionError
import de.chrgroth.james.platform.domain.model.app.DisplayTextExpressionEvaluator
import de.chrgroth.james.platform.domain.model.app.DisplayTextTemplate
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyValueFormat
import de.chrgroth.james.platform.domain.report.ReportExpressions
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock

/**
 * Evaluates the `${ expression }` placeholders of display texts with the expression language ([Expression]): the entity's properties by name (also
 * nullable ones) and its computed properties are readable, `config.<field>` and the functions reading the entity's other objects are not. The
 * result is formatted like a `{property}` value: dates `dd.MM.yyyy`, times `HH:mm`, numbers with German separators, yes/no as `ja`/`nein`; a
 * placeholder naming just a computed property is formatted like that property (with its unit). Without a value, or when evaluation fails, the
 * result is empty - rendering never throws.
 */
internal object DisplayTextExpressions : DisplayTextExpressionEvaluator {

  override fun evaluate(entity: EntityDefinition, data: Map<String, String?>, source: String): String = runCatching { render(entity, data, source) }.getOrDefault("")

  private fun render(entity: EntityDefinition, data: Map<String, String?>, source: String): String {
    val expression = EntityExpressions.parseOrNull(source) ?: return ""
    val computedNames = entity.computedProperties.mapTo(HashSet()) { it.name }
    val computed = if (expression.identifiers.any { it in computedNames }) {
      ComputedPropertyService().computeValues(entity, data, Clock.System.now(), emptyMap(), null)
    } else {
      emptyMap()
    }
    val bare = entity.computedProperties.find { it.name == source.trim() }
    if (bare != null) return computed[bare.id.value]?.takeIf { it.isNotBlank() }?.let { PropertyValueFormat.format(bare.type, bare.unit, it) }.orEmpty()
    val context = EntityExpressionContext(entity.properties, data, emptyMap(), null, EntityExpressions.localNow(Clock.System.now()), extraField = { name ->
      entity.computedProperties.find { it.name == name }?.let { ExpressionValue.of(computed[it.id.value], ReportExpressions.typeOf(it.type)) }
    })
    return format(expression.evaluate(context))
  }

  /** [value] as shown to users, like a stored property value of the matching type. */
  fun format(value: ExpressionValue?): String = when (value) {
    null -> ""
    is ExpressionValue.Text -> value.value
    is ExpressionValue.Num -> PropertyValueFormat.formatNumber(value.value)
    is ExpressionValue.Bool -> if (value.value) "ja" else "nein"
    is ExpressionValue.Date -> PropertyValueFormat.format(PropertyType.DATE, null, value.value.toString())
    is ExpressionValue.DateTime -> PropertyValueFormat.format(PropertyType.DATETIME, null, value.value.toString())
    is ExpressionValue.Time -> PropertyValueFormat.format(PropertyType.TIME, null, value.value.toString())
  }

  /**
   * Validates a display text template on save: every `{property}` must be a non-nullable property (as before), every `${expression}` a valid
   * expression over the entity's properties and computed properties; an unclosed `${` is a syntax error. Null if the template is fine.
   */
  fun validate(entity: EntityDefinition, template: String): InvalidExpressionError? {
    if (DisplayTextTemplate.unclosedExpression(template)) return InvalidExpressionError(ExpressionErrorCode.SYNTAX, listOf(template.indexOf("\${").plus(1).toString(), "missing '}'"))
    return DisplayTextTemplate.expressions(template).firstNotNullOfOrNull { EntityExpressions.validateDisplayText(entity, it) }
  }
}

/** The domain's [DisplayTextExpressionEvaluator], handed to the adapters' [de.chrgroth.james.platform.domain.model.app.DisplayTextResolver]. */
@ApplicationScoped
@Suppress("Unused")
class DisplayTextExpressionService : DisplayTextExpressionEvaluator {
  override fun evaluate(entity: EntityDefinition, data: Map<String, String?>, source: String): String = DisplayTextExpressions.evaluate(entity, data, source)
}
