package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.expression.EntityExpressions
import de.chrgroth.james.platform.domain.expression.Expression
import de.chrgroth.james.platform.domain.expression.ExpressionContext
import de.chrgroth.james.platform.domain.expression.ExpressionType
import de.chrgroth.james.platform.domain.expression.ExpressionValue
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyType
import java.time.LocalDate
import kotlin.math.roundToLong

/** How reports use the expression language ([Expression]): types of result fields, typed calculated fields and the evaluation context of a row. */
internal object ReportExpressions {

  /** The types a calculated field (`calc` with `type`) can have. */
  val RESULT_TYPES: List<PropertyType> = listOf(
    PropertyType.DOUBLE,
    PropertyType.LONG,
    PropertyType.STRING,
    PropertyType.BOOLEAN,
    PropertyType.DATE,
    PropertyType.DATETIME,
    PropertyType.TIME,
  )

  /** The expression type a value of [type] is read as; references are read as their display text, lists and objects as text. */
  fun typeOf(type: PropertyType?): ExpressionType? = when (type) {
    null -> null
    PropertyType.LONG, PropertyType.DOUBLE -> ExpressionType.NUMBER
    PropertyType.BOOLEAN -> ExpressionType.BOOLEAN
    PropertyType.DATE -> ExpressionType.DATE
    PropertyType.DATETIME -> ExpressionType.DATETIME
    PropertyType.TIME -> ExpressionType.TIME
    PropertyType.STRING, PropertyType.REF, PropertyType.LIST, PropertyType.OBJECT -> ExpressionType.TEXT
  }

  /** The field type an expression result of [type] gets without an explicit `type` (unknown: DOUBLE, as before typed results existed). */
  fun propertyTypeOf(type: ExpressionType?): PropertyType = when (type) {
    null, ExpressionType.NUMBER -> PropertyType.DOUBLE
    ExpressionType.TEXT -> PropertyType.STRING
    ExpressionType.BOOLEAN -> PropertyType.BOOLEAN
    ExpressionType.DATE -> PropertyType.DATE
    ExpressionType.DATETIME -> PropertyType.DATETIME
    ExpressionType.TIME -> PropertyType.TIME
  }

  /** Whether a result of [from] can be stored as [to]: anything as text, numbers and yes/no among each other, dates and date-times, times. */
  fun converts(from: ExpressionType, to: PropertyType): Boolean = when (to) {
    PropertyType.STRING -> true
    PropertyType.LONG, PropertyType.DOUBLE, PropertyType.BOOLEAN -> from.numeric || from == ExpressionType.TEXT
    PropertyType.DATE, PropertyType.DATETIME -> from.temporal || from == ExpressionType.TEXT
    PropertyType.TIME -> from == ExpressionType.TIME || from == ExpressionType.DATETIME || from == ExpressionType.TEXT
    else -> false
  }

  /** [value] in the raw storage form of [type] (numbers plain - LONG rounded -, yes/no as `true`/`false`, dates and times ISO); null if it does not fit. */
  fun raw(value: ExpressionValue?, type: PropertyType): String? = when (type) {
    PropertyType.DOUBLE -> ExpressionValue.toNumber(value)?.takeIf { it.isFinite() }?.toString()
    PropertyType.LONG -> ExpressionValue.toNumber(value)?.takeIf { it.isFinite() }?.roundToLong()?.toString()
    PropertyType.BOOLEAN -> when (value) {
      null -> null
      is ExpressionValue.Text -> value.value.trim().lowercase().toBooleanStrictOrNull()?.toString()
      else -> ExpressionValue.truthy(value).toString()
    }
    PropertyType.DATE -> ExpressionValue.toDate(value)?.toString()
    PropertyType.DATETIME -> ExpressionValue.toDateTime(value)?.toString()
    PropertyType.TIME -> ExpressionValue.toTime(value)?.toString()
    else -> ExpressionValue.toText(value)
  }

  /** Parses [source], null if it is invalid (definitions are validated before they run, so this only guards against stale ones). */
  fun parseOrNull(source: String): Expression? = runCatching { Expression.parse(source) }.getOrNull()
}

/**
 * The context an expression is evaluated in for one result row: its fields typed by the result's [columns] (unknown types are guessed,
 * references read as their display text via [label]), `config.<field>` via [config], configuration LISTs for list functions via [configList]
 * (the LIST property and its raw value) and [today].
 */
internal class ReportRowContext(
  private val values: Map<String, String?>,
  private val columns: Map<String, ResultColumn>,
  private val config: (String) -> ExpressionValue?,
  override val today: LocalDate,
  private val label: (ResultColumn, String?) -> String,
  private val configList: (String) -> Pair<Property, String?>? = { null },
) : ExpressionContext {

  override fun field(name: String): ExpressionValue? {
    val raw = values[name]
    val column = columns[name]
    if (column?.type == PropertyType.REF && !raw.isNullOrEmpty()) return ExpressionValue.Text(label(column, raw))
    return ExpressionValue.of(raw, ReportExpressions.typeOf(column?.type))
  }

  override fun config(path: String): ExpressionValue? = config.invoke(path)

  override fun configItems(path: String): List<ExpressionContext>? = configList(path)?.let { (list, raw) -> EntityExpressions.itemContexts(this, list, raw) }
}
