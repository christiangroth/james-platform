package de.chrgroth.james.platform.domain.imports

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.chrgroth.james.platform.domain.model.imports.FilterMode
import de.chrgroth.james.platform.domain.model.imports.FilterOperator
import de.chrgroth.james.platform.domain.model.imports.FilterRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FilterEvaluatorTests {

  private val objectMapper = jacksonObjectMapper()

  private fun records(json: String) = objectMapper.readTree(json).toList()

  @Test
  fun `no rules keep every record`() {
    val result = FilterEvaluator.apply(records("""[{"name":"Alice"},{"name":"Bob"}]"""), emptyList())

    assertThat(result).hasSize(2)
  }

  @Test
  fun `include rule narrows the record set down to matches`() {
    val rules = listOf(FilterRule(FilterMode.INCLUDE, "country", FilterOperator.EQUALS, "DE"))

    val result = FilterEvaluator.apply(records("""[{"country":"DE"},{"country":"US"},{"country":"DE"}]"""), rules)

    assertThat(result.map { it.get("country").asText() }).containsExactly("DE", "DE")
  }

  @Test
  fun `exclude rule removes matches from the record set`() {
    val rules = listOf(FilterRule(FilterMode.EXCLUDE, "country", FilterOperator.EQUALS, "US"))

    val result = FilterEvaluator.apply(records("""[{"country":"DE"},{"country":"US"},{"country":"DE"}]"""), rules)

    assertThat(result.map { it.get("country").asText() }).containsExactly("DE", "DE")
  }

  @Test
  fun `include and exclude rules are applied in order as a pipeline`() {
    val rules = listOf(
      FilterRule(FilterMode.INCLUDE, "country", FilterOperator.EQUALS, "DE"),
      FilterRule(FilterMode.EXCLUDE, "city", FilterOperator.EQUALS, "Berlin"),
    )

    val result = FilterEvaluator.apply(
      records("""[{"country":"DE","city":"Berlin"},{"country":"DE","city":"Munich"},{"country":"US","city":"Berlin"}]"""),
      rules,
    )

    assertThat(result.map { it.get("city").asText() }).containsExactly("Munich")
  }

  @Test
  fun `is null operator matches missing and explicit null values`() {
    val rules = listOf(FilterRule(FilterMode.INCLUDE, "middleName", FilterOperator.IS_NULL))

    val result = FilterEvaluator.apply(records("""[{"middleName":"Jane"},{"middleName":null},{}]"""), rules)

    assertThat(result).hasSize(2)
  }

  @Test
  fun `is not null operator matches only records with a present, non-null value`() {
    val rules = listOf(FilterRule(FilterMode.INCLUDE, "middleName", FilterOperator.IS_NOT_NULL))

    val result = FilterEvaluator.apply(records("""[{"middleName":"Jane"},{"middleName":null},{}]"""), rules)

    assertThat(result.map { it.get("middleName").asText() }).containsExactly("Jane")
  }

  @Test
  fun `not equals operator matches missing and differing values`() {
    val rules = listOf(FilterRule(FilterMode.INCLUDE, "status", FilterOperator.NOT_EQUALS, "archived"))

    val result = FilterEvaluator.apply(records("""[{"status":"active"},{"status":"archived"},{}]"""), rules)

    assertThat(result).hasSize(2)
  }

  @Test
  fun `contains operator matches a substring of the source value`() {
    val rules = listOf(FilterRule(FilterMode.INCLUDE, "email", FilterOperator.CONTAINS, "@example.com"))

    val result = FilterEvaluator.apply(records("""[{"email":"a@example.com"},{"email":"b@other.com"}]"""), rules)

    assertThat(result.map { it.get("email").asText() }).containsExactly("a@example.com")
  }

  @Test
  fun `nested source paths are resolved dot-separated`() {
    val rules = listOf(FilterRule(FilterMode.INCLUDE, "address.city", FilterOperator.EQUALS, "Berlin"))

    val result = FilterEvaluator.apply(records("""[{"address":{"city":"Berlin"}},{"address":{"city":"Munich"}}]"""), rules)

    assertThat(result).hasSize(1)
  }
}
