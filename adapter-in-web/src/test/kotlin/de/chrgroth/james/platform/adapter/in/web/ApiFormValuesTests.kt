package de.chrgroth.james.platform.adapter.`in`.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiFormValuesTests {

  @Test
  fun `the items of a LIST of OBJECTs become one column per field, aligned by item`() {
    val form = ApiFormValues.toFormData(mapOf("sessions" to listOf(mapOf("lane-length" to 25, "lanes" to 40), mapOf("lanes" to 20))))

    assertThat(form).containsExactlyInAnyOrderEntriesOf(mapOf("prop_sessions.lane-length" to listOf("25", ""), "prop_sessions.lanes" to listOf("40", "20")))
  }

  @Test
  fun `scalar lists stay one value per item`() {
    assertThat(ApiFormValues.toFormData(mapOf("tags" to listOf("a", "b")))).isEqualTo(mapOf("prop_tags" to listOf("a", "b")))
  }
}
