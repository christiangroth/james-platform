package de.chrgroth.james.platform.domain.imports

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.chrgroth.james.platform.domain.model.imports.SchemaProperty
import de.chrgroth.james.platform.domain.model.imports.SchemaPropertyType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SchemaDetectorTests {

  private val objectMapper = jacksonObjectMapper()

  @Test
  fun `detect determines mandatory property present on every object`() {
    val root = objectMapper.readTree("""{"items":[{"a":1},{"a":2}]}""")

    val result = SchemaDetector.detect(root, "items")

    assertThat(result).containsExactly(SchemaProperty("a", mapOf(SchemaPropertyType.LONG to 2), mandatory = true))
  }

  @Test
  fun `detect determines optional property missing on some objects`() {
    val root = objectMapper.readTree("""{"items":[{"a":1},{}]}""")

    val result = SchemaDetector.detect(root, "items")

    assertThat(result).containsExactly(SchemaProperty("a", mapOf(SchemaPropertyType.LONG to 1), mandatory = false))
  }

  @Test
  fun `detect counts multiple occurring types per property`() {
    val root = objectMapper.readTree("""{"items":[{"a":1},{"a":"text"},{"a":true}]}""")

    val result = SchemaDetector.detect(root, "items")

    assertThat(result).containsExactly(
      SchemaProperty(
        "a",
        mapOf(SchemaPropertyType.LONG to 1, SchemaPropertyType.STRING to 1, SchemaPropertyType.BOOLEAN to 1),
        mandatory = true,
      ),
    )
  }

  @Test
  fun `detect distinguishes integral numbers from decimal numbers`() {
    val root = objectMapper.readTree("""{"items":[{"a":1},{"a":2.5}]}""")

    val result = SchemaDetector.detect(root, "items")

    assertThat(result).containsExactly(
      SchemaProperty("a", mapOf(SchemaPropertyType.LONG to 1, SchemaPropertyType.DOUBLE to 1), mandatory = true),
    )
  }

  @Test
  fun `detect recognizes ISO date strings as DATE`() {
    val root = objectMapper.readTree("""{"items":[{"a":"2024-01-15"},{"a":"2024-02-20"}]}""")

    val result = SchemaDetector.detect(root, "items")

    assertThat(result).containsExactly(SchemaProperty("a", mapOf(SchemaPropertyType.DATE to 2), mandatory = true))
  }

  @Test
  fun `detect recognizes ISO datetime strings as DATETIME`() {
    val root = objectMapper.readTree(
      """{"items":[{"a":"2024-01-15T10:30:00Z"},{"a":"2024-01-15T10:30:00.123+02:00"}]}""",
    )

    val result = SchemaDetector.detect(root, "items")

    assertThat(result).containsExactly(SchemaProperty("a", mapOf(SchemaPropertyType.DATETIME to 2), mandatory = true))
  }

  @Test
  fun `detect treats non-matching strings as STRING`() {
    val root = objectMapper.readTree("""{"items":[{"a":"not a date"},{"a":"2024-01"}]}""")

    val result = SchemaDetector.detect(root, "items")

    assertThat(result).containsExactly(SchemaProperty("a", mapOf(SchemaPropertyType.STRING to 2), mandatory = true))
  }

  @Test
  fun `detect recurses into nested objects`() {
    val root = objectMapper.readTree("""{"items":[{"address":{"city":"Berlin"}},{"address":{"city":"Munich"}}]}""")

    val result = SchemaDetector.detect(root, "items")

    assertThat(result).containsExactlyInAnyOrder(
      SchemaProperty("address", mapOf(SchemaPropertyType.OBJECT to 2), mandatory = true),
      SchemaProperty("address.city", mapOf(SchemaPropertyType.STRING to 2), mandatory = true),
    )
  }

  @Test
  fun `detect marks nested property optional when parent object is missing`() {
    val root = objectMapper.readTree("""{"items":[{"address":{"city":"Berlin"}},{}]}""")

    val result = SchemaDetector.detect(root, "items")

    assertThat(result).containsExactlyInAnyOrder(
      SchemaProperty("address", mapOf(SchemaPropertyType.OBJECT to 1), mandatory = false),
      SchemaProperty("address.city", mapOf(SchemaPropertyType.STRING to 1), mandatory = false),
    )
  }

  @Test
  fun `detect does not descend into arrays`() {
    val root = objectMapper.readTree("""{"items":[{"tags":[{"name":"x"}]}]}""")

    val result = SchemaDetector.detect(root, "items")

    assertThat(result).containsExactly(SchemaProperty("tags", mapOf(SchemaPropertyType.ARRAY to 1), mandatory = true))
  }

  @Test
  fun `detect treats explicit null values as present with type null`() {
    val root = objectMapper.readTree("""{"items":[{"a":null},{"a":1}]}""")

    val result = SchemaDetector.detect(root, "items")

    assertThat(result).containsExactly(
      SchemaProperty("a", mapOf(SchemaPropertyType.NULL to 1, SchemaPropertyType.LONG to 1), mandatory = true),
    )
  }

  @Test
  fun `detect resolves the data path before deriving the schema`() {
    val root = objectMapper.readTree("""{"data":{"nested":{"items":[{"a":1}]}}}""")

    val result = SchemaDetector.detect(root, "data.nested.items")

    assertThat(result).containsExactly(SchemaProperty("a", mapOf(SchemaPropertyType.LONG to 1), mandatory = true))
  }
}
