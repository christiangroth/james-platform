package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.model.imports.SchemaProperty
import de.chrgroth.james.platform.domain.model.imports.SchemaPropertyType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SchemaDriftTests {

  private fun property(path: String) = SchemaProperty(path = path, typeCounts = mapOf(SchemaPropertyType.STRING to 1), mandatory = true)

  @Test
  fun `not detected when the same set of property paths is present`() {
    val baseline = listOf(property("a"), property("b"))
    val current = listOf(property("b"), property("a"))

    assertThat(SchemaDrift.detected(baseline, current)).isFalse()
  }

  @Test
  fun `not detected when only per-run statistics differ`() {
    val baseline = listOf(SchemaProperty(path = "a", typeCounts = mapOf(SchemaPropertyType.LONG to 1), mandatory = true))
    val current = listOf(SchemaProperty(path = "a", typeCounts = mapOf(SchemaPropertyType.LONG to 5), mandatory = false))

    assertThat(SchemaDrift.detected(baseline, current)).isFalse()
  }

  @Test
  fun `detected when a property was added`() {
    val baseline = listOf(property("a"))
    val current = listOf(property("a"), property("b"))

    assertThat(SchemaDrift.detected(baseline, current)).isTrue()
  }

  @Test
  fun `detected when a property was removed`() {
    val baseline = listOf(property("a"), property("b"))
    val current = listOf(property("a"))

    assertThat(SchemaDrift.detected(baseline, current)).isTrue()
  }
}
