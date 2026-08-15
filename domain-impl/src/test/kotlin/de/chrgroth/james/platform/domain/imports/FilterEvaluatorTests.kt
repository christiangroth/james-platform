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

  @Test
  fun `matches operator matches records whose value contains a match of the regex pattern`() {
    val rules = listOf(FilterRule(FilterMode.INCLUDE, "email", FilterOperator.MATCHES, "^[a-z]+@example\\.com$"))

    val result = FilterEvaluator.apply(records("""[{"email":"alice@example.com"},{"email":"bob@other.com"},{"email":"Bob2@example.com"}]"""), rules)

    assertThat(result.map { it.get("email").asText() }).containsExactly("alice@example.com")
  }

  @Test
  fun `not matches operator matches missing values and values without a match of the regex pattern`() {
    val rules = listOf(FilterRule(FilterMode.INCLUDE, "email", FilterOperator.NOT_MATCHES, "^[a-z]+@example\\.com$"))

    val result = FilterEvaluator.apply(records("""[{"email":"alice@example.com"},{"email":"bob@other.com"},{}]"""), rules)

    assertThat(result.map { it.get("email")?.asText() }).containsExactly("bob@other.com", null)
  }

  @Test
  fun `matches operator treats an invalid regex pattern as no match`() {
    val rules = listOf(FilterRule(FilterMode.INCLUDE, "email", FilterOperator.MATCHES, "["))

    val result = FilterEvaluator.apply(records("""[{"email":"alice@example.com"}]"""), rules)

    assertThat(result).isEmpty()
  }

  @Test
  fun `greater than operator compares numeric values numerically`() {
    val rules = listOf(FilterRule(FilterMode.INCLUDE, "age", FilterOperator.GREATER_THAN, "30"))

    val result = FilterEvaluator.apply(records("""[{"age":25},{"age":30},{"age":31},{"age":100}]"""), rules)

    assertThat(result.map { it.get("age").asInt() }).containsExactly(31, 100)
  }

  @Test
  fun `greater than or equal operator includes the boundary value`() {
    val rules = listOf(FilterRule(FilterMode.INCLUDE, "age", FilterOperator.GREATER_THAN_OR_EQUAL, "30"))

    val result = FilterEvaluator.apply(records("""[{"age":29},{"age":30},{"age":31}]"""), rules)

    assertThat(result.map { it.get("age").asInt() }).containsExactly(30, 31)
  }

  @Test
  fun `less than and less than or equal operators compare numeric values numerically`() {
    val lessThan = FilterEvaluator.apply(
      records("""[{"age":29},{"age":30},{"age":31}]"""),
      listOf(FilterRule(FilterMode.INCLUDE, "age", FilterOperator.LESS_THAN, "30")),
    )
    val lessThanOrEqual = FilterEvaluator.apply(
      records("""[{"age":29},{"age":30},{"age":31}]"""),
      listOf(FilterRule(FilterMode.INCLUDE, "age", FilterOperator.LESS_THAN_OR_EQUAL, "30")),
    )

    assertThat(lessThan.map { it.get("age").asInt() }).containsExactly(29)
    assertThat(lessThanOrEqual.map { it.get("age").asInt() }).containsExactly(29, 30)
  }

  @Test
  fun `comparison operators compare zero-padded ISO date strings lexicographically`() {
    val rules = listOf(FilterRule(FilterMode.INCLUDE, "date", FilterOperator.GREATER_THAN, "2024-06-01"))

    val result = FilterEvaluator.apply(records("""[{"date":"2024-05-31"},{"date":"2024-06-01"},{"date":"2024-06-02"}]"""), rules)

    assertThat(result.map { it.get("date").asText() }).containsExactly("2024-06-02")
  }

  @Test
  fun `include absent flag additionally matches missing and explicit null values regardless of operator`() {
    val rules = listOf(FilterRule(FilterMode.INCLUDE, "status", FilterOperator.EQUALS, "active", includeAbsent = true))

    val result = FilterEvaluator.apply(records("""[{"status":"active"},{"status":"archived"},{"status":null},{}]"""), rules)

    assertThat(result.map { it.get("status")?.takeIf { value -> !value.isNull }?.asText() }).containsExactly("active", null, null)
  }

  @Test
  fun `include absent flag has no effect when a value is present and does not match`() {
    val rules = listOf(FilterRule(FilterMode.INCLUDE, "status", FilterOperator.EQUALS, "active", includeAbsent = true))

    val result = FilterEvaluator.apply(records("""[{"status":"archived"}]"""), rules)

    assertThat(result).isEmpty()
  }

  @Test
  fun `distinctValues returns the sorted distinct non-null values found at the given path`() {
    val result = FilterEvaluator.distinctValues(records("""[{"country":"DE"},{"country":"US"},{"country":"DE"},{"country":null},{}]"""), "country")

    assertThat(result).containsExactly("DE", "US")
  }
}
