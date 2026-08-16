package de.chrgroth.james.platform.domain.imports

import com.fasterxml.jackson.databind.JsonNode
import de.chrgroth.james.platform.domain.model.imports.FilterMode
import de.chrgroth.james.platform.domain.model.imports.FilterOperator
import de.chrgroth.james.platform.domain.model.imports.FilterRule
import java.util.Collections
import java.util.IdentityHashMap

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

  /**
   * The complement of [apply]: every record from [records] that did NOT survive the [rules] pipeline, in original
   * order - for the filter step's "view excluded records" preview. Told apart from the matched records by identity
   * (not equality), so records with identical content are still counted correctly.
   */
  fun excluded(records: List<JsonNode>, rules: List<FilterRule>): List<JsonNode> {
    val matched = Collections.newSetFromMap(IdentityHashMap<JsonNode, Boolean>())
    matched.addAll(apply(records, rules))
    return records.filterNot { matched.contains(it) }
  }

  /** Distinct textual values found at [sourcePath] across [records], sorted - used to let the filter UI offer known values instead of free text input for [FilterOperator.EQUALS]/[FilterOperator.NOT_EQUALS]. */
  fun distinctValues(records: List<JsonNode>, sourcePath: String): List<String> =
    records.mapNotNull { resolve(it, sourcePath)?.takeIf { value -> !value.isNull }?.asText() }
      .distinct()
      .sorted()

  private fun matches(record: JsonNode, rule: FilterRule): Boolean {
    val value = resolve(record, rule.sourcePath)?.takeIf { !it.isNull }
      ?: return rule.includeAbsent ||
      rule.operator == FilterOperator.IS_NULL || rule.operator == FilterOperator.NOT_EQUALS || rule.operator == FilterOperator.NOT_MATCHES

    val text = value.asText()
    return when (rule.operator) {
      FilterOperator.IS_NULL -> false
      FilterOperator.IS_NOT_NULL -> true
      FilterOperator.EQUALS -> text == rule.value
      FilterOperator.NOT_EQUALS -> text != rule.value
      FilterOperator.CONTAINS -> text.contains(rule.value.orEmpty())
      FilterOperator.MATCHES -> matchesRegex(text, rule.value)
      FilterOperator.NOT_MATCHES -> !matchesRegex(text, rule.value)
      FilterOperator.GREATER_THAN -> compare(value, rule.value) > 0
      FilterOperator.GREATER_THAN_OR_EQUAL -> compare(value, rule.value) >= 0
      FilterOperator.LESS_THAN -> compare(value, rule.value) < 0
      FilterOperator.LESS_THAN_OR_EQUAL -> compare(value, rule.value) <= 0
    }
  }

  private fun matchesRegex(text: String, pattern: String?): Boolean =
    pattern?.let { runCatching { Regex(it).containsMatchIn(text) }.getOrDefault(false) } ?: false

  /** Compares numerically when both sides parse as numbers, lexicographically otherwise - which also covers the zero-padded ISO date/datetime strings [SchemaDetector] recognizes, since those sort correctly as plain text. */
  private fun compare(value: JsonNode, ruleValue: String?): Int {
    val ruleNumber = ruleValue?.toDoubleOrNull()
    return if (value.isNumber && ruleNumber != null) {
      value.asDouble().compareTo(ruleNumber)
    } else {
      value.asText().compareTo(ruleValue.orEmpty())
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
