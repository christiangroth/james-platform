package de.chrgroth.james.platform.domain.model.app

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ObjectListFormatTests {

  private val laneLength = Property(
    id = PropertyId("lane-length"),
    name = "bahnlänge",
    type = PropertyType.LONG,
    unit = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.METERS, DistanceGranularity.METERS),
  )
  private val lanes = Property(id = PropertyId("lanes"), name = "bahnen", type = PropertyType.LONG)
  private val sessions = Property(
    id = PropertyId("sessions"),
    name = "sessions",
    type = PropertyType.LIST,
    listItemType = PropertyType.OBJECT,
    nestedProperties = listOf(laneLength, lanes),
  )

  @Test
  fun `a LIST of OBJECTs is recognized and uses its nested properties`() {
    assertThat(sessions.isObjectList).isTrue()
    assertThat(sessions.hasNestedProperties).isTrue()
    assertThat(sessions.copy(listItemType = PropertyType.STRING).hasNestedProperties).isFalse()
  }

  @Test
  fun `a LIST of OBJECTs is formatted item by item with formatted field values`() {
    val raw = encodeListValue(listOf(encodeObjectValue(mapOf("lane-length" to "25", "lanes" to "40")), encodeObjectValue(mapOf("lane-length" to "50", "lanes" to "1200"))))

    assertThat(PropertyValueFormat.format(sessions, raw)).isEqualTo("25m, 40 | 50m, 1.200")
  }
}
