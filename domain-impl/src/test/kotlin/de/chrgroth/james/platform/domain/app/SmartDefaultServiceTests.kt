package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SmartDefaultServiceTests {

  private val service = SmartDefaultService()

  private val fixedNow = Instant.parse("2024-06-15T10:30:00Z")

  @Test
  fun `computeSmartDefaults returns empty map when no properties have smart defaults`() {
    val entity = EntityDefinition(
      id = EntityDefinitionId("e-1"),
      name = "Order",
      properties = listOf(
        Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG),
        Property(id = PropertyId("p-2"), name = "Note", type = PropertyType.STRING),
      ),
    )

    val result = service.computeSmartDefaults(entity, fixedNow)

    assertThat(result).isEmpty()
  }

  @Test
  fun `computeSmartDefaults evaluates simple string literal`() {
    val entity = EntityDefinition(
      id = EntityDefinitionId("e-1"),
      name = "Order",
      properties = listOf(
        Property(id = PropertyId("p-1"), name = "Status", type = PropertyType.STRING, smartDefault = "\"active\""),
      ),
    )

    val result = service.computeSmartDefaults(entity, fixedNow)

    assertThat(result["p-1"]).isEqualTo("active")
  }

  @Test
  fun `computeSmartDefaults provides now as Instant binding`() {
    val entity = EntityDefinition(
      id = EntityDefinitionId("e-1"),
      name = "Order",
      properties = listOf(
        Property(id = PropertyId("p-1"), name = "CreatedAt", type = PropertyType.DATETIME, smartDefault = "now.toString()"),
      ),
    )

    val result = service.computeSmartDefaults(entity, fixedNow)

    assertThat(result["p-1"]).isEqualTo(fixedNow.toString())
  }

  @Test
  fun `computeSmartDefaults processes properties in order and accumulates in it binding`() {
    val entity = EntityDefinition(
      id = EntityDefinitionId("e-1"),
      name = "Order",
      properties = listOf(
        Property(id = PropertyId("p-1"), name = "Base", type = PropertyType.STRING, smartDefault = "\"hello\""),
        Property(id = PropertyId("p-2"), name = "Derived", type = PropertyType.STRING, smartDefault = "it[\"p-1\"] + \" world\""),
      ),
    )

    val result = service.computeSmartDefaults(entity, fixedNow)

    assertThat(result["p-1"]).isEqualTo("hello")
    assertThat(result["p-2"]).isEqualTo("hello world")
  }

  @Test
  fun `computeSmartDefaults skips null result gracefully`() {
    val entity = EntityDefinition(
      id = EntityDefinitionId("e-1"),
      name = "Order",
      properties = listOf(
        Property(id = PropertyId("p-1"), name = "NullProp", type = PropertyType.STRING, smartDefault = "null"),
      ),
    )

    val result = service.computeSmartDefaults(entity, fixedNow)

    assertThat(result["p-1"]).isNull()
  }

  @Test
  fun `computeSmartDefaults skips property with failing script and continues with rest`() {
    val entity = EntityDefinition(
      id = EntityDefinitionId("e-1"),
      name = "Order",
      properties = listOf(
        Property(id = PropertyId("p-1"), name = "Bad", type = PropertyType.STRING, smartDefault = "this is not valid kotlin!!!"),
        Property(id = PropertyId("p-2"), name = "Good", type = PropertyType.STRING, smartDefault = "\"ok\""),
      ),
    )

    val result = service.computeSmartDefaults(entity, fixedNow)

    assertThat(result["p-2"]).isEqualTo("ok")
  }

  @Test
  fun `computeSmartDefaults only includes properties with smart defaults in result`() {
    val entity = EntityDefinition(
      id = EntityDefinitionId("e-1"),
      name = "Order",
      properties = listOf(
        Property(id = PropertyId("p-1"), name = "NoSmartDefault", type = PropertyType.STRING),
        Property(id = PropertyId("p-2"), name = "WithSmartDefault", type = PropertyType.STRING, smartDefault = "\"filled\""),
      ),
    )

    val result = service.computeSmartDefaults(entity, fixedNow)

    assertThat(result).doesNotContainKey("p-1")
    assertThat(result["p-2"]).isEqualTo("filled")
  }
}
