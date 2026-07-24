package de.chrgroth.james.platform.domain.imports

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.chrgroth.james.platform.domain.model.imports.DataPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DataPathDetectorTests {

  private val objectMapper = jacksonObjectMapper()

  @Test
  fun `detect finds an array of objects at the root level`() {
    val root = objectMapper.readTree("""{"items":[{"a":1},{"a":2}]}""")

    val result = DataPathDetector.detect(root)

    assertThat(result).containsExactly(DataPath("items", 2))
  }

  @Test
  fun `detect finds an array of objects nested within objects`() {
    val root = objectMapper.readTree("""{"data":{"nested":{"items":[{"a":1},{"a":2},{"a":3}]}}}""")

    val result = DataPathDetector.detect(root)

    assertThat(result).containsExactly(DataPath("data.nested.items", 3))
  }

  @Test
  fun `detect finds multiple candidate paths`() {
    val root = objectMapper.readTree("""{"a":[{"x":1}],"b":{"c":[{"y":1},{"y":2}]}}""")

    val result = DataPathDetector.detect(root)

    assertThat(result).containsExactlyInAnyOrder(DataPath("a", 1), DataPath("b.c", 2))
  }

  @Test
  fun `detect ignores nesting inside a matched array`() {
    val root = objectMapper.readTree("""{"items":[{"nested":[{"a":1}]}]}""")

    val result = DataPathDetector.detect(root)

    assertThat(result).containsExactly(DataPath("items", 1))
  }

  @Test
  fun `detect ignores arrays containing non-object elements`() {
    val root = objectMapper.readTree("""{"items":[1,2,3],"mixed":[{"a":1},2]}""")

    val result = DataPathDetector.detect(root)

    assertThat(result).isEmpty()
  }

  @Test
  fun `detect ignores empty arrays`() {
    val root = objectMapper.readTree("""{"items":[]}""")

    val result = DataPathDetector.detect(root)

    assertThat(result).isEmpty()
  }

  @Test
  fun `detect returns empty list when no array of objects exists`() {
    val root = objectMapper.readTree("""{"foo":"bar","nested":{"baz":42}}""")

    val result = DataPathDetector.detect(root)

    assertThat(result).isEmpty()
  }

  @Test
  fun `resolve returns matching data path for a valid path`() {
    val root = objectMapper.readTree("""{"data":{"items":[{"a":1},{"a":2}]}}""")

    val result = DataPathDetector.resolve(root, "data.items")

    assertThat(result).isEqualTo(DataPath("data.items", 2))
  }

  @Test
  fun `resolve returns null for a blank path`() {
    val root = objectMapper.readTree("""{"items":[{"a":1}]}""")

    val result = DataPathDetector.resolve(root, "  ")

    assertThat(result).isNull()
  }

  @Test
  fun `resolve returns null for an unknown path`() {
    val root = objectMapper.readTree("""{"items":[{"a":1}]}""")

    val result = DataPathDetector.resolve(root, "unknown")

    assertThat(result).isNull()
  }

  @Test
  fun `resolve returns null when path points to a non-array value`() {
    val root = objectMapper.readTree("""{"foo":"bar"}""")

    val result = DataPathDetector.resolve(root, "foo")

    assertThat(result).isNull()
  }

  @Test
  fun `resolve returns null when path points to an array with non-object elements`() {
    val root = objectMapper.readTree("""{"items":[1,2,3]}""")

    val result = DataPathDetector.resolve(root, "items")

    assertThat(result).isNull()
  }
}
