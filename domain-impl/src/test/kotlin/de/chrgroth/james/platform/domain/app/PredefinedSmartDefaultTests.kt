package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.expression.EntityExpressions
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.PredefinedSmartDefault
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PredefinedSmartDefaultTests {

  @Test
  fun `every predefined smart default is a valid expression for each type it is offered for`() {
    PredefinedSmartDefault.entries.forEach { predefined ->
      predefined.types.forEach { type ->
        val property = Property(id = PropertyId("p-1"), name = "feld", type = type, targetEntityId = EntityDefinitionId("e-2").takeIf { type == PropertyType.REF })
        val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "E", properties = listOf(property))

        assertThat(EntityExpressions.validateSmartDefault(entity, property, predefined.expressionFor(property.name), null))
          .describedAs("${predefined.name} for $type")
          .isNull()
      }
    }
  }

  @Test
  fun `predefined smart defaults are offered per type`() {
    assertThat(PredefinedSmartDefault.forType(PropertyType.DATE))
      .containsExactly(PredefinedSmartDefault.DATE_TODAY, PredefinedSmartDefault.DATE_NEXT_DAY, PredefinedSmartDefault.DATE_END_OF_MONTH)
    assertThat(PredefinedSmartDefault.forType(PropertyType.REF)).containsExactly(PredefinedSmartDefault.LAST_VALUE)
    assertThat(PredefinedSmartDefault.forType(PropertyType.LIST)).isEmpty()
    assertThat(PredefinedSmartDefault.byTypeName["TIME"]).containsExactly(PredefinedSmartDefault.TIME_NOW)
    assertThat(PredefinedSmartDefault.DATE_NEXT_DAY.expressionFor("datum")).isEqualTo("coalesce(next_day(datum), today())")
  }
}
