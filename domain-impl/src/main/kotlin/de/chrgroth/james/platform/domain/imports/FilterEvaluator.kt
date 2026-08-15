package de.chrgroth.james.platform.domain.imports

import com.fasterxml.jackson.databind.JsonNode
import de.chrgroth.james.platform.domain.model.imports.FilterMode
import de.chrgroth.james.platform.domain.model.imports.FilterOperator
import de.chrgroth.james.platform.domain.model.imports.FilterRule

/** Applies a job's filter rules to its source records, in order, as a narrowing/removing pipeline (see [FilterRule]). */
object FilterEvaluator {

  private const val PATH_SEPARATOR = "."

  fun apply(records: List<JsonNode>, rules: List<FilterRule>): List<JsonNode> =
    rules.fold(records) { current, rule ->
      when (rule.mode) {
        FilterMode.INCLUDE -> current.filter { matches(it, rule) }
        FilterMode.EXCLUDE -> current.filterNot { matches(it, rule) }
      }
    }

  private fun matches(record: JsonNode, rule: FilterRule): Boolean {
    val value = resolve(record, rule.sourcePath)?.takeIf { !it.isNull }
      ?: return rule.operator == FilterOperator.IS_NULL || rule.operator == FilterOperator.NOT_EQUALS

    val text = value.asText()
    return when (rule.operator) {
      FilterOperator.IS_NULL -> false
      FilterOperator.IS_NOT_NULL -> true
      FilterOperator.EQUALS -> text == rule.value
      FilterOperator.NOT_EQUALS -> text != rule.value
      FilterOperator.CONTAINS -> text.contains(rule.value.orEmpty())
    }
  }

  private fun resolve(record: JsonNode, path: String): JsonNode? {
    var current: JsonNode = record
    for (segment in path.split(PATH_SEPARATOR)) {
      current = current.get(segment) ?: return null
    }
    return current
  }
}
