package de.chrgroth.james.platform.domain.model.app

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formatting of stored property values as shown to Users, shared by display texts, aggregation labels and reports: dates `dd.MM.yyyy`,
 * datetimes `dd.MM.yyyy HH:mm`, times `HH:mm`, numbers with German separators (up to two decimals), unit properties via [formatUnitValue]
 * (e.g. `10km 234m`). LIST values are formatted per item and joined with ", ". Anything else is shown as stored.
 */
object PropertyValueFormat {

  private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy")
  private val DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
  private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
  private val NUMBER_FORMAT = ThreadLocal.withInitial { DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.GERMANY)) }

  /** Formats a number with up to two decimals and German grouping/decimal separators, e.g. `1.234,5`. */
  fun formatNumber(value: Double): String = NUMBER_FORMAT.get().format(value)

  fun formatDate(date: LocalDate): String = DATE_FORMATTER.format(date)

  fun format(property: Property, raw: String): String =
    if (property.isObjectList) formatObjectList(property, raw) else format(property.type, property.unit, raw, property.listItemType)

  /** A LIST of OBJECTs as its items separated by ` | `, each item as its formatted field values separated by `, `, e.g. `25m, 40 | 50m, 20`. */
  private fun formatObjectList(property: Property, raw: String): String = decodeListValue(raw).joinToString(" | ") { item ->
    val values = runCatching { decodeObjectValue(item) }.getOrDefault(emptyMap())
    property.nestedProperties.mapNotNull { field -> values[field.id.value]?.toString()?.takeIf { it.isNotBlank() }?.let { format(field, it) } }.joinToString(", ")
  }

  fun format(type: PropertyType, unit: PropertyUnit?, raw: String, listItemType: PropertyType? = null): String = when (type) {
    PropertyType.DATE -> runCatching { LocalDate.parse(raw) }.getOrNull()?.let { DATE_FORMATTER.format(it) } ?: raw
    PropertyType.DATETIME -> runCatching { LocalDateTime.parse(raw) }.getOrNull()?.let { DATETIME_FORMATTER.format(it) } ?: raw
    PropertyType.TIME -> runCatching { LocalTime.parse(raw) }.getOrNull()?.let { TIME_FORMATTER.format(it) } ?: raw
    PropertyType.LONG -> raw.toLongOrNull()?.let { formatNumeric(unit, it.toDouble()) } ?: raw
    PropertyType.DOUBLE -> raw.toDoubleOrNull()?.let { formatNumeric(unit, it) } ?: raw
    PropertyType.LIST -> sortListItems(listItemType, decodeListValue(raw)).joinToString(", ") { item -> listItemType?.let { format(it, null, item) } ?: item }
    else -> raw
  }

  private fun formatNumeric(unit: PropertyUnit?, value: Double): String =
    if (unit != null) formatUnitValue(BigDecimal.valueOf(value), unit) else formatNumber(value)
}

/**
 * Resolves Entity display texts within one installation. Placeholder values are formatted via [PropertyValueFormat]; a REF placeholder shows
 * the referenced object's own display text (at most [MAX_REF_DEPTH] levels deep, so reference cycles terminate), else its id. Without a
 * display text template, the generic reference text (Entity name, id and unique key values) is used.
 */
class DisplayTextResolver(
  private val entityById: Map<String, EntityDefinition>,
  allAppData: List<AppData>,
) {
  private val appDataById: Map<String, AppData> by lazy { allAppData.associateBy { it.id.value } }

  /** Display text of the object [refId] points to, or [refId] itself if it does not resolve. */
  fun displayTextOf(refId: String): String {
    val target = appDataById[refId] ?: return refId
    return displayText(entityById[target.entityType.value], refId, target.data)
  }

  fun displayText(entityDef: EntityDefinition?, dataId: String, data: Map<String, String?>): String = displayText(entityDef, dataId, data, 0)

  private fun displayText(entityDef: EntityDefinition?, dataId: String, data: Map<String, String?>, depth: Int): String {
    if (entityDef == null) return dataId
    val template = entityDef.displayText ?: return referenceText(entityDef, dataId, data)
    val propertiesByName = entityDef.properties.associateBy { it.name }
    val result = TOKEN_REGEX.replace(template) { match ->
      val key = match.groupValues[1]
      if (key == "id") {
        dataId
      } else {
        val property = propertiesByName[key]
        if (property == null) "<?>" else formatValue(property, data[property.id.value], depth)
      }
    }.trim()
    return result.ifBlank { referenceText(entityDef, dataId, data) }
  }

  private fun formatValue(property: Property, raw: String?, depth: Int): String {
    if (raw.isNullOrBlank()) return ""
    if (property.type != PropertyType.REF) return PropertyValueFormat.format(property, raw)
    if (depth >= MAX_REF_DEPTH) return raw
    val target = appDataById[raw] ?: return raw
    return displayText(entityById[target.entityType.value], raw, target.data, depth + 1)
  }

  private fun referenceText(entityDef: EntityDefinition, dataId: String, data: Map<String, String?>): String {
    val uniqueValues = entityDef.properties
      .filter { it.constraints.contains(PropertyConstraint.UniqueKey) }
      .map { data[it.id.value] ?: "" }
    return "${entityDef.name} $dataId [${uniqueValues.joinToString(", ")}]"
  }

  companion object {
    private val TOKEN_REGEX = Regex("\\{([^}]+)\\}")

    /** How many REF levels a display text resolves, e.g. Zählerstand -> Zähler -> Zuhause; deeper references show their id. */
    const val MAX_REF_DEPTH = 2
  }
}
