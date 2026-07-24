package de.chrgroth.james.platform.domain.imports

import com.fasterxml.jackson.databind.JsonNode
import de.chrgroth.james.platform.domain.model.imports.SchemaProperty
import de.chrgroth.james.platform.domain.model.imports.SchemaPropertyType

/**
 * Derives a schema for the objects found at a given data path: for every property, including nested ones inside
 * objects, all occurring value types are counted and whether the property is present on every object (mandatory)
 * or not (optional). Arrays are never descended into, matching DataPathDetector's dot-path semantics. Numbers are
 * split into LONG (integral) and DOUBLE (fractional), and textual values matching a date or datetime pattern are
 * reported as DATE/DATETIME rather than STRING.
 */
object SchemaDetector {

  private const val PATH_SEPARATOR = "."

  private val DATE_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}$""")
  private val DATETIME_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:?\d{2})?$""")

  fun detect(root: JsonNode, path: String): List<SchemaProperty> {
    val objects = resolveObjects(root, path)
    val typeCounts = linkedMapOf<String, MutableMap<SchemaPropertyType, Int>>()
    val presenceCounts = linkedMapOf<String, Int>()

    objects.forEach { walk(it, emptyList(), typeCounts, presenceCounts) }

    return typeCounts.map { (propertyPath, counts) ->
      SchemaProperty(
        path = propertyPath,
        typeCounts = counts.toMap(),
        mandatory = presenceCounts.getValue(propertyPath) == objects.size,
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
  ) {
    node.properties().forEach { (key, value) ->
      val currentPath = (path + key).joinToString(PATH_SEPARATOR)
      val counts = typeCounts.getOrPut(currentPath) { linkedMapOf() }
      val type = value.schemaPropertyType()
      counts[type] = (counts[type] ?: 0) + 1
      presenceCounts[currentPath] = (presenceCounts[currentPath] ?: 0) + 1

      if (value.isObject) {
        walk(value, path + key, typeCounts, presenceCounts)
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
