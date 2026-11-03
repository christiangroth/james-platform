package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.ComputedProperty
import de.chrgroth.james.platform.domain.model.app.ComputedPropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.TimeGranularity
import de.chrgroth.james.platform.domain.model.app.UnitFamily
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReportFieldsTests {

  @Test
  fun `a computed property keeps its unit`() {
    val unit = PropertyUnit(UnitFamily.TIME, TimeGranularity.SECONDS, TimeGranularity.MINUTES)
    val pace = ComputedProperty(ComputedPropertyId("c-pace"), "pace", PropertyType.DOUBLE, "dauer / (distanz / 1000)", unit)

    val field = ResolvedField("pace", ReportFixtures.fahrt, refProperty = null, property = null, computed = pace)

    assertThat(field.unit).isEqualTo(unit)
  }

  @Test
  fun `a computed property without unit has none`() {
    val jahr = ComputedProperty(ComputedPropertyId("c-jahr"), "jahr", PropertyType.STRING, "year(datum)")

    val field = ResolvedField("jahr", ReportFixtures.fahrt, refProperty = null, property = null, computed = jahr)

    assertThat(field.unit).isNull()
  }
}
