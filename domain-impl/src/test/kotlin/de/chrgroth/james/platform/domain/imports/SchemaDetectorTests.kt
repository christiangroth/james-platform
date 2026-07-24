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

    assertThat(result).containsExactly(SchemaProperty("a", mapOf(SchemaPropertyType.NUMBER to 2), mandatory = true))
  }

  @Test
  fun `detect determines optional property missing on some objects`() {
    val root = objectMapper.readTree("""{"items":[{"a":1},{}]}""")

    val result = SchemaDetector.detect(root, "items")

    assertThat(result).containsExactly(SchemaProperty("a", mapOf(SchemaPropertyType.NUMBER to 1), mandatory = false))
  }

  @Test
  fun `detect counts multiple occurring types per property`() {
    val root = objectMapper.readTree("""{"items":[{"a":1},{"a":"text"},{"a":true}]}""")

    val result = SchemaDetector.detect(root, "items")

    assertThat(result).containsExactly(
      SchemaProperty(
        "a",
        mapOf(SchemaPropertyType.NUMBER to 1, SchemaPropertyType.STRING to 1, SchemaPropertyType.BOOLEAN to 1),
        mandatory = true,
      ),
    )
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
      SchemaProperty("a", mapOf(SchemaPropertyType.NULL to 1, SchemaPropertyType.NUMBER to 1), mandatory = true),
    )
  }

  @Test
  fun `detect resolves the data path before deriving the schema`() {
    val root = objectMapper.readTree("""{"data":{"nested":{"items":[{"a":1}]}}}""")

    val result = SchemaDetector.detect(root, "data.nested.items")

    assertThat(result).containsExactly(SchemaProperty("a", mapOf(SchemaPropertyType.NUMBER to 1), mandatory = true))
  }
}
