package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.app.ComputedProperty
import de.chrgroth.james.platform.domain.model.app.ComputedPropertyId
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ComputedPropertyServiceTests {

  private val service = ComputedPropertyService()

  private val fixedNow = Instant.parse("2024-06-15T10:30:00Z")
  private val entityId = EntityDefinitionId("e-1")

  @Test
  fun `computeValues returns empty map when no computed properties have scripts`() {
    val entity = EntityDefinition(
      id = entityId,
      name = "Order",
      computedProperties = listOf(
        ComputedProperty(id = ComputedPropertyId("cp-1"), name = "Total", type = PropertyType.LONG),
      ),
    )

    val result = service.computeValues(entity, emptyMap(), fixedNow)

    assertThat(result).isEmpty()
  }

  @Test
  fun `computeValues returns empty map when no computed properties are defined`() {
    val entity = EntityDefinition(id = entityId, name = "Order")

    val result = service.computeValues(entity, emptyMap(), fixedNow)

    assertThat(result).isEmpty()
  }

  @Test
  fun `computeValues evaluates simple string literal`() {
    val entity = EntityDefinition(
      id = entityId,
      name = "Order",
      computedProperties = listOf(
        ComputedProperty(id = ComputedPropertyId("cp-1"), name = "Label", type = PropertyType.STRING, script = "\"computed\""),
      ),
    )

    val result = service.computeValues(entity, emptyMap(), fixedNow)

    assertThat(result["cp-1"]).isEqualTo("computed")
  }

  @Test
  fun `computeValues provides it binding with entity data`() {
    val entity = EntityDefinition(
      id = entityId,
      name = "Order",
      computedProperties = listOf(
        ComputedProperty(id = ComputedPropertyId("cp-1"), name = "PriceLabel", type = PropertyType.STRING, script = "\"Price: \" + (it[\"price\"] ?: \"n/a\")"),
      ),
    )

    val result = service.computeValues(entity, mapOf("price" to "42"), fixedNow)

    assertThat(result["cp-1"]).isEqualTo("Price: 42")
  }

  @Test
  fun `computeValues provides now as Instant binding`() {
    val entity = EntityDefinition(
      id = entityId,
      name = "Order",
      computedProperties = listOf(
        ComputedProperty(id = ComputedPropertyId("cp-1"), name = "Timestamp", type = PropertyType.DATETIME, script = "now.toString()"),
      ),
    )

    val result = service.computeValues(entity, emptyMap(), fixedNow)

    assertThat(result["cp-1"]).isEqualTo(fixedNow.toString())
  }

  @Test
  fun `computeValues processes computed properties in order and accumulated results available via computed binding`() {
    val entity = EntityDefinition(
      id = entityId,
      name = "Order",
      computedProperties = listOf(
        ComputedProperty(id = ComputedPropertyId("cp-1"), name = "Base", type = PropertyType.STRING, script = "\"hello\""),
        ComputedProperty(id = ComputedPropertyId("cp-2"), name = "Extended", type = PropertyType.STRING, script = "computed[\"cp-1\"] + \" world\""),
      ),
    )

    val result = service.computeValues(entity, emptyMap(), fixedNow)

    assertThat(result["cp-1"]).isEqualTo("hello")
    assertThat(result["cp-2"]).isEqualTo("hello world")
  }

  @Test
  fun `computeValues skips null result gracefully`() {
    val entity = EntityDefinition(
      id = entityId,
      name = "Order",
      computedProperties = listOf(
        ComputedProperty(id = ComputedPropertyId("cp-1"), name = "NullProp", type = PropertyType.STRING, script = "null"),
      ),
    )

    val result = service.computeValues(entity, emptyMap(), fixedNow)

    assertThat(result["cp-1"]).isNull()
  }

  @Test
  fun `computeValues skips computed property with failing script and continues with rest`() {
    val entity = EntityDefinition(
      id = entityId,
      name = "Order",
      computedProperties = listOf(
        ComputedProperty(id = ComputedPropertyId("cp-1"), name = "Bad", type = PropertyType.STRING, script = "this is not valid kotlin!!!"),
        ComputedProperty(id = ComputedPropertyId("cp-2"), name = "Good", type = PropertyType.STRING, script = "\"ok\""),
      ),
    )

    val result = service.computeValues(entity, emptyMap(), fixedNow)

    assertThat(result["cp-2"]).isEqualTo("ok")
  }

  @Test
  fun `computeValues only includes computed properties with scripts in result`() {
    val entity = EntityDefinition(
      id = entityId,
      name = "Order",
      computedProperties = listOf(
        ComputedProperty(id = ComputedPropertyId("cp-1"), name = "NoScript", type = PropertyType.STRING),
        ComputedProperty(id = ComputedPropertyId("cp-2"), name = "WithScript", type = PropertyType.STRING, script = "\"filled\""),
      ),
    )

    val result = service.computeValues(entity, emptyMap(), fixedNow)

    assertThat(result).doesNotContainKey("cp-1")
    assertThat(result["cp-2"]).isEqualTo("filled")
  }

  @Test
  fun `computeValues can perform numeric calculation from entity data`() {
    val entity = EntityDefinition(
      id = entityId,
      name = "Order",
      computedProperties = listOf(
        ComputedProperty(id = ComputedPropertyId("cp-1"), name = "Total", type = PropertyType.LONG, script = "(it[\"qty\"]?.toLongOrNull() ?: 0L) * (it[\"price\"]?.toLongOrNull() ?: 0L)"),
      ),
    )

    val result = service.computeValues(entity, mapOf("qty" to "3", "price" to "10"), fixedNow)

    assertThat(result["cp-1"]).isEqualTo("30")
  }
}
