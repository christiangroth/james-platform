package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.ReportAggregateFunction
import de.chrgroth.james.platform.domain.model.app.TimeBucket
import de.chrgroth.james.platform.domain.model.app.parseUnitValue

internal enum class ColumnRole { FIELD, BUCKET, GROUP, VALUE }

/**
 * One field of a query's result: a queried entity field, or - for an aggregate query - its time bucket, group or an aggregated value. [type] is
 * null when unknown (schema-only validation without entities). Values are always kept in their raw storage form (unit values in the storage
 * granularity, references as ids, buckets as bucket keys).
 */
internal data class ResultColumn(
  val path: String,
  val role: ColumnRole,
  val type: PropertyType?,
  val unit: PropertyUnit? = null,
  val listItemType: PropertyType? = null,
  val bucket: TimeBucket? = null,
) {
  val numeric: Boolean get() = type != null && ReportFields.isNumericType(type)

  companion object {
    fun of(field: ResolvedField, role: ColumnRole = ColumnRole.FIELD) = ResultColumn(field.path, role, field.type, field.unit, field.listItemType)

    /** The column of an aggregated value: COUNT is a unitless LONG, AVG a DOUBLE in the source unit, the others keep the source type and unit. */
    fun value(name: String, function: ReportAggregateFunction, source: ResultColumn?): ResultColumn = when (function) {
      ReportAggregateFunction.COUNT -> ResultColumn(name, ColumnRole.VALUE, PropertyType.LONG)
      ReportAggregateFunction.AVG -> ResultColumn(name, ColumnRole.VALUE, source?.type?.let { PropertyType.DOUBLE }, source?.unit)
      else -> ResultColumn(name, ColumnRole.VALUE, source?.type, source?.unit)
    }
  }
}

internal object ReportValues {

  /** Color names a threshold may use besides `#rgb`/`#rrggbb`; the renderer maps them to theme colors. */
  val COLOR_NAMES = listOf("green", "red", "orange", "yellow", "blue", "purple", "gray")
  private val HEX_COLOR = Regex("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")

  fun isColor(value: String): Boolean = value in COLOR_NAMES || HEX_COLOR.matches(value)

  /** A threshold value in the raw storage form: a plain number (`.`/`,` decimal separator) or, for a field with unit, a unit text (`1000km`). */
  fun parseNumber(raw: String, unit: PropertyUnit?): Double? {
    val trimmed = raw.trim()
    if (unit != null) return parseUnitValue(trimmed, unit.storageGranularity)?.toDouble()
    return trimmed.replace(',', '.').toDoubleOrNull()
  }
}
