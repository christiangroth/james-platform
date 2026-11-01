package de.chrgroth.james.platform.domain.model.app

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UniqueKeysTests {

  private val at = Property(id = PropertyId("at"), name = "At", type = PropertyType.STRING, constraints = setOf(PropertyConstraint.UniqueKey))
  private val meter = Property(id = PropertyId("meter"), name = "Meter", type = PropertyType.STRING, constraints = setOf(PropertyConstraint.UniqueKey))
  private val value = Property(id = PropertyId("value"), name = "Value", type = PropertyType.DOUBLE)
  private val entity = EntityDefinition(id = EntityDefinitionId("e"), name = "Reading", properties = listOf(at, meter, value))

  @Test
  fun `unique key properties are all properties carrying the UniqueKey constraint`() {
    assertThat(entity.uniqueKeyProperties).containsExactly(at, meter)
  }

  @Test
  fun `comparison values only contain values of objects matching the candidate on every other key property`() {
    val existing = listOf(
      mapOf(at.id to "2026-01-01", meter.id to "Strom"),
      mapOf(at.id to "2026-01-01", meter.id to "Wasser"),
      mapOf(at.id to "2026-01-02", meter.id to "Strom"),
    )
    val candidate = mapOf(at.id to "2026-01-01", meter.id to "Strom")

    assertThat(uniqueKeyComparisonValues(at, entity.uniqueKeyProperties, candidate, existing)).containsExactly("2026-01-01", "2026-01-02")
    assertThat(uniqueKeyComparisonValues(meter, entity.uniqueKeyProperties, candidate, existing)).containsExactly("Strom", "Wasser")
  }

  @Test
  fun `comparison values for a single key property are all existing values`() {
    val single = EntityDefinition(id = EntityDefinitionId("e"), name = "Bike", properties = listOf(at, value))
    val existing = listOf(mapOf(at.id to "a"), mapOf(at.id to "b"), mapOf(at.id to null))

    assertThat(uniqueKeyComparisonValues(at, single.uniqueKeyProperties, mapOf(at.id to "a"), existing)).containsExactly("a", "b")
  }

  @Test
  fun `comparison values for a property outside the key are empty`() {
    assertThat(uniqueKeyComparisonValues(value, entity.uniqueKeyProperties, emptyMap(), listOf(mapOf(at.id to "x")))).isEmpty()
  }
}
