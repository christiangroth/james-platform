package de.chrgroth.james.platform.domain.model.app

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** LIST values of dates, times and numbers are kept and shown ascending; other lists keep the order their items were entered in. */
class ListValueOrderTests {

  @Test
  fun `dates, date-times, times and numbers are sorted ascending, unparseable items last`() {
    assertThat(sortListItems(PropertyType.DATE, listOf("2026-12-25", "2026-01-01", "kaputt", "2026-04-03"))).containsExactly("2026-01-01", "2026-04-03", "2026-12-25", "kaputt")
    assertThat(sortListItems(PropertyType.DATETIME, listOf("2026-01-01T10:00", "2025-12-31T23:59"))).containsExactly("2025-12-31T23:59", "2026-01-01T10:00")
    assertThat(sortListItems(PropertyType.TIME, listOf("14:00", "08:30", "10:15"))).containsExactly("08:30", "10:15", "14:00")
    assertThat(sortListItems(PropertyType.LONG, listOf("10", "9", "-1"))).containsExactly("-1", "9", "10")
    assertThat(sortListItems(PropertyType.DOUBLE, listOf("2.5", "10", "0.75"))).containsExactly("0.75", "2.5", "10")
  }

  @Test
  fun `texts, references and yes or no values keep their entry order`() {
    assertThat(sortListItems(PropertyType.STRING, listOf("b", "a", "c"))).containsExactly("b", "a", "c")
    assertThat(sortListItems(PropertyType.REF, listOf("id-2", "id-1"))).containsExactly("id-2", "id-1")
    assertThat(sortListItems(null, listOf("b", "a"))).containsExactly("b", "a")
  }

  @Test
  fun `a LIST of dates is displayed ascending even if stored in another order`() {
    val feiertage = Property(id = PropertyId("feiertage"), name = "feiertage", type = PropertyType.LIST, listItemType = PropertyType.DATE)

    assertThat(PropertyValueFormat.format(feiertage, encodeListValue(listOf("2026-12-25", "2026-01-01")))).isEqualTo("01.01.2026, 25.12.2026")
  }
}
