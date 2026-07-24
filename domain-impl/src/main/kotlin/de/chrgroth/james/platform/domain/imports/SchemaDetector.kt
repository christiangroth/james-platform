package de.chrgroth.james.platform.domain.imports

import com.fasterxml.jackson.databind.JsonNode
import de.chrgroth.james.platform.domain.model.imports.NumericRange
import de.chrgroth.james.platform.domain.model.imports.SchemaProperty
import de.chrgroth.james.platform.domain.model.imports.SchemaPropertyType

/**
 * Derives a schema for the objects found at a given data path: for every property, including nested ones inside
 * objects, all occurring value types are counted and whether the property is present on every object (mandatory)
 * or not (optional). Arrays are never descended into, matching DataPathDetector's dot-path semantics. Numbers are
 * split into LONG (integral) and DOUBLE (fractional), and textual values matching a date or datetime pattern are
 * reported as DATE/DATETIME rather than STRING. For numeric properties the observed min/max value is tracked, and
 * for STRING properties the observed value lengths are counted, so that later constraint checks against a target
 * model have statistics to compare against.
 */
object SchemaDetector {

  private const val PATH_SEPARATOR = "."

  private val DATE_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}$""")
  private val DATETIME_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:?\d{2})?$""")

  fun detect(root: JsonNode, path: String): List<SchemaProperty> {
    val objects = resolveObjects(root, path)
    val typeCounts = linkedMapOf<String, MutableMap<SchemaPropertyType, Int>>()
    val presenceCounts = linkedMapOf<String, Int>()
    val numericRanges = linkedMapOf<String, NumericRange>()
    val stringLengthCounts = linkedMapOf<String, MutableMap<Int, Int>>()

    objects.forEach { walk(it, emptyList(), typeCounts, presenceCounts, numericRanges, stringLengthCounts) }

    return typeCounts.map { (propertyPath, counts) ->
      SchemaProperty(
        path = propertyPath,
        typeCounts = counts.toMap(),
        mandatory = presenceCounts.getValue(propertyPath) == objects.size,
        numericRange = numericRanges[propertyPath],
        stringLengthCounts = stringLengthCounts[propertyPath]?.toMap() ?: emptyMap(),
      )
    }
  }

  private fun resolveObjects(root: JsonNode, path: String): List<JsonNode> {
    var current = root
    for (segment in path.split(PATH_SEPARATOR)) {
      current = current.get(segment)
    }
    return current.toList()
  }

  private fun walk(
    node: JsonNode,
    path: List<String>,
    typeCounts: MutableMap<String, MutableMap<SchemaPropertyType, Int>>,
    presenceCounts: MutableMap<String, Int>,
    numericRanges: MutableMap<String, NumericRange>,
    stringLengthCounts: MutableMap<String, MutableMap<Int, Int>>,
  ) {
    node.properties().forEach { (key, value) ->
      val currentPath = (path + key).joinToString(PATH_SEPARATOR)
      val counts = typeCounts.getOrPut(currentPath) { linkedMapOf() }
      val type = value.schemaPropertyType()
      counts[type] = (counts[type] ?: 0) + 1
      presenceCounts[currentPath] = (presenceCounts[currentPath] ?: 0) + 1

      when (type) {
        SchemaPropertyType.LONG, SchemaPropertyType.DOUBLE -> {
          val numericValue = value.asDouble()
          val existing = numericRanges[currentPath]
          numericRanges[currentPath] = if (existing == null) {
            NumericRange(min = numericValue, max = numericValue)
          } else {
            NumericRange(min = minOf(existing.min, numericValue), max = maxOf(existing.max, numericValue))
          }
        }

        SchemaPropertyType.STRING -> {
          val length = value.asText().length
          val lengthCounts = stringLengthCounts.getOrPut(currentPath) { linkedMapOf() }
          lengthCounts[length] = (lengthCounts[length] ?: 0) + 1
        }

        else -> Unit
      }

      if (value.isObject) {
        walk(value, path + key, typeCounts, presenceCounts, numericRanges, stringLengthCounts)
      }
    }
  }

  private fun JsonNode.schemaPropertyType(): SchemaPropertyType = when {
    isObject -> SchemaPropertyType.OBJECT
    isArray -> SchemaPropertyType.ARRAY
    isTextual -> asText().textualSchemaPropertyType()
    isBoolean -> SchemaPropertyType.BOOLEAN
    isIntegralNumber -> SchemaPropertyType.LONG
    isFloatingPointNumber -> SchemaPropertyType.DOUBLE
    else -> SchemaPropertyType.NULL
  }

  private fun String.textualSchemaPropertyType(): SchemaPropertyType = when {
    DATETIME_PATTERN.matches(this) -> SchemaPropertyType.DATETIME
    DATE_PATTERN.matches(this) -> SchemaPropertyType.DATE
    else -> SchemaPropertyType.STRING
  }
}
