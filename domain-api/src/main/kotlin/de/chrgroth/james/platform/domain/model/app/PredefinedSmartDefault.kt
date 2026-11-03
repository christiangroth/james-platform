package de.chrgroth.james.platform.domain.model.app

/**
 * Predefined smart default expressions (ADR 0029) the developer UI offers instead of writing one by hand. `{field}` in [expression] stands for the
 * name of the property the smart default is set on.
 */
enum class PredefinedSmartDefault(
  val label: String,
  val expression: String,
  val types: Set<PropertyType>,
) {
  DATE_TODAY(
    label = "Heute",
    expression = "today()",
    types = setOf(PropertyType.DATE),
  ),
  DATE_NEXT_DAY(
    label = "Tag nach dem letzten Eintrag",
    expression = "coalesce(next_day({field}), today())",
    types = setOf(PropertyType.DATE),
  ),
  DATE_END_OF_MONTH(
    label = "Monatsende",
    expression = "end_of_month(today())",
    types = setOf(PropertyType.DATE),
  ),
  TIME_NOW(
    label = "Jetzt",
    expression = "time(now())",
    types = setOf(PropertyType.TIME),
  ),
  DATETIME_NOW(
    label = "Jetzt",
    expression = "now()",
    types = setOf(PropertyType.DATETIME),
  ),
  LAST_VALUE(
    label = "Zuletzt verwendeter Wert",
    expression = "last({field})",
    types = setOf(PropertyType.LONG, PropertyType.DOUBLE, PropertyType.BOOLEAN, PropertyType.STRING, PropertyType.REF),
  ),
  ;

  /** The expression for the property named [fieldName]. */
  fun expressionFor(fieldName: String): String = expression.replace(FIELD_PLACEHOLDER, fieldName)

  companion object {
    const val FIELD_PLACEHOLDER = "{field}"

    /** Returns all predefined smart defaults applicable to the given [type]. */
    fun forType(type: PropertyType): List<PredefinedSmartDefault> = entries.filter { type in it.types }

    /** Predefined smart defaults grouped by their applicable [PropertyType] name, supporting multi-type entries. */
    val byTypeName: Map<String, List<PredefinedSmartDefault>> =
      entries.flatMap { pd -> pd.types.map { type -> type.name to pd } }
        .groupBy({ it.first }, { it.second })
  }
}
