package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.app.PredefinedSmartDefault
import de.chrgroth.james.platform.domain.model.app.PropertyType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PredefinedSmartDefaultTests {

  @Test
  fun `forType returns empty list for types without predefined defaults`() {
    TYPES_WITHOUT_PREDEFINED_DEFAULTS.forEach { type ->
      assertThat(PredefinedSmartDefault.forType(type))
        .describedAs("$type should have no predefined smart defaults")
        .isEmpty()
    }
  }

  private companion object {
    val TYPES_WITHOUT_PREDEFINED_DEFAULTS = listOf(
      PropertyType.STRING,
      PropertyType.LONG,
      PropertyType.DOUBLE,
      PropertyType.BOOLEAN,
      PropertyType.DURATION,
      PropertyType.LIST,
      PropertyType.OBJECT,
      PropertyType.REF
    )
  }

  @Test
  fun `forType returns DATE_TODAY for DATE type`() {
    assertThat(PredefinedSmartDefault.forType(PropertyType.DATE))
      .containsExactly(PredefinedSmartDefault.DATE_TODAY)
  }

  @Test
  fun `forType returns all TIME predefined defaults in order`() {
    assertThat(PredefinedSmartDefault.forType(PropertyType.TIME))
      .containsExactly(
        PredefinedSmartDefault.TIME_NOW,
        PredefinedSmartDefault.TIME_NOW_CURRENT_SECOND,
        PredefinedSmartDefault.TIME_NOW_CURRENT_MINUTE,
        PredefinedSmartDefault.TIME_NOW_CURRENT_HOUR,
      )
  }

  @Test
  fun `forType returns all DATETIME predefined defaults in order`() {
    assertThat(PredefinedSmartDefault.forType(PropertyType.DATETIME))
      .containsExactly(
        PredefinedSmartDefault.DATETIME_NOW,
        PredefinedSmartDefault.DATETIME_NOW_CURRENT_SECOND,
        PredefinedSmartDefault.DATETIME_NOW_CURRENT_MINUTE,
        PredefinedSmartDefault.DATETIME_NOW_CURRENT_HOUR,
      )
  }

  @Test
  fun `byTypeName groups all predefined defaults by type name`() {
    val byType = PredefinedSmartDefault.byTypeName

    assertThat(byType.keys).containsExactlyInAnyOrder("DATE", "TIME", "DATETIME")
    assertThat(byType["DATE"]).containsExactly(PredefinedSmartDefault.DATE_TODAY)
    assertThat(byType["TIME"]).containsExactly(
      PredefinedSmartDefault.TIME_NOW,
      PredefinedSmartDefault.TIME_NOW_CURRENT_SECOND,
      PredefinedSmartDefault.TIME_NOW_CURRENT_MINUTE,
      PredefinedSmartDefault.TIME_NOW_CURRENT_HOUR,
    )
    assertThat(byType["DATETIME"]).containsExactly(
      PredefinedSmartDefault.DATETIME_NOW,
      PredefinedSmartDefault.DATETIME_NOW_CURRENT_SECOND,
      PredefinedSmartDefault.DATETIME_NOW_CURRENT_MINUTE,
      PredefinedSmartDefault.DATETIME_NOW_CURRENT_HOUR,
    )
  }
}
