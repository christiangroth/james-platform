package de.chrgroth.james.platform.domain.expression

import de.chrgroth.james.platform.domain.error.ExpressionErrorCode
import de.chrgroth.james.platform.domain.model.app.ComputedProperty
import de.chrgroth.james.platform.domain.model.app.ComputedPropertyId
import de.chrgroth.james.platform.domain.model.app.DistanceGranularity
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.UnitFamily
import de.chrgroth.james.platform.domain.model.app.encodeListValue
import de.chrgroth.james.platform.domain.model.app.encodeObjectValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/** List functions (`sum_of`, `avg_of`, `min_of`, `max_of`, `count_of`) over a LIST of OBJECTs and a list of simple values. */
class ListFunctionTests {

  private val laneLength = Property(
    id = PropertyId("p-len"),
    name = "bahnlänge",
    type = PropertyType.LONG,
    unit = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.METERS, DistanceGranularity.METERS),
  )
  private val lanes = Property(id = PropertyId("p-lanes"), name = "bahnen", type = PropertyType.LONG)
  private val note = Property(id = PropertyId("p-note"), name = "notiz", type = PropertyType.STRING)
  private val sessions = Property(
    id = PropertyId("p-sessions"),
    name = "sessions",
    type = PropertyType.LIST,
    listItemType = PropertyType.OBJECT,
    nestedProperties = listOf(laneLength, lanes, note),
  )
  private val times = Property(id = PropertyId("p-times"), name = "zeiten", type = PropertyType.LIST, listItemType = PropertyType.LONG)
  private val datum = Property(id = PropertyId("p-datum"), name = "datum", type = PropertyType.DATE)
  private val config = EntityDefinition(
    id = EntityDefinitionId("e-config"),
    name = "Einstellungen",
    configuration = true,
    properties = listOf(Property(id = PropertyId("c-faktor"), name = "faktor", type = PropertyType.DOUBLE)),
  )
  private val entity = EntityDefinition(id = EntityDefinitionId("e-swim"), name = "Schwimmen", properties = listOf(datum, sessions, times))

  private fun item(length: String?, count: String?) = encodeObjectValue(mapOf("p-len" to length, "p-lanes" to count))

  private val data = mapOf(
    "p-datum" to "2026-11-04",
    "p-sessions" to encodeListValue(listOf(item("25", "40"), item("50", "20"), item("25", null))),
    "p-times" to encodeListValue(listOf("60", "90")),
  )

  private fun eval(source: String, values: Map<String, String?> = data): ExpressionValue? = Expression.parse(source).evaluate(
    EntityExpressionContext(entity.properties, values, mapOf("c-faktor" to "2"), config, LocalDateTime.of(2026, 11, 4, 12, 0)),
  )

  private fun validate(source: String) =
    EntityExpressions.validateComputed(entity, ComputedProperty(ComputedPropertyId("c-1"), "strecke", PropertyType.LONG, source), source, config)

  @Test
  fun `sum_of adds up the per-item expression and skips items without value`() {
    assertThat(eval("sum_of(sessions, bahnlänge * bahnen)")).isEqualTo(ExpressionValue.Num(2000.0))
    assertThat(eval("sum_of(sessions, bahnen)")).isEqualTo(ExpressionValue.Num(60.0))
    assertThat(eval("sum_of(zeiten, item)")).isEqualTo(ExpressionValue.Num(150.0))
  }

  @Test
  fun `avg_of, min_of and max_of over the items with a value`() {
    assertThat(eval("avg_of(sessions, bahnlänge * bahnen)")).isEqualTo(ExpressionValue.Num(1000.0))
    assertThat(eval("min_of(sessions, bahnlänge)")).isEqualTo(ExpressionValue.Num(25.0))
    assertThat(eval("max_of(sessions, bahnlänge * bahnen * config.faktor)")).isEqualTo(ExpressionValue.Num(2000.0))
  }

  @Test
  fun `count_of counts all items or the matching ones`() {
    assertThat(eval("count_of(sessions)")).isEqualTo(ExpressionValue.Num(3.0))
    assertThat(eval("count_of(sessions, bahnlänge == 25)")).isEqualTo(ExpressionValue.Num(2.0))
    assertThat(eval("count_of(sessions, present(bahnen))")).isEqualTo(ExpressionValue.Num(2.0))
  }

  @Test
  fun `an empty list sums up to 0 and has no average, minimum or maximum`() {
    val empty = mapOf("p-datum" to "2026-11-04")
    assertThat(eval("sum_of(sessions, bahnen)", empty)).isEqualTo(ExpressionValue.Num(0.0))
    assertThat(eval("count_of(sessions)", empty)).isEqualTo(ExpressionValue.Num(0.0))
    assertThat(eval("avg_of(sessions, bahnen)", empty)).isNull()
    assertThat(eval("max_of(sessions, bahnen)", empty)).isNull()
  }

  @Test
  fun `the list of a list function is a top-level field, the item fields are read only per item`() {
    val expression = Expression.parse("sum_of(sessions, bahnlänge * bahnen * config.faktor) + count_of(zeiten)")

    assertThat(expression.identifiers).containsExactlyInAnyOrder("sessions", "config.faktor", "zeiten")
    assertThat(expression.itemIdentifiers).isEqualTo(mapOf("sessions" to setOf("bahnlänge", "bahnen"), "zeiten" to emptySet<String>()))
  }

  @Test
  fun `computed properties validate list functions`() {
    assertThat(validate("sum_of(sessions, bahnlänge * bahnen)")).isNull()
    assertThat(validate("sum_of(zeiten, item)")).isNull()
    assertThat(validate("sum_of(datum, bahnen)")?.reason).isEqualTo(ExpressionErrorCode.LIST_FIELD_REQUIRED)
    assertThat(validate("sum_of(sessions, tiefe)")?.reason).isEqualTo(ExpressionErrorCode.UNKNOWN_ITEM_FIELD)
    assertThat(validate("sum_of(zeiten, bahnen)")?.reason).isEqualTo(ExpressionErrorCode.UNKNOWN_ITEM_FIELD)
    assertThat(validate("sum_of(sessions, notiz)")?.reason).isEqualTo(ExpressionErrorCode.ARGUMENT_TYPE)
    assertThat(validate("sum_of('x', bahnen)")?.reason).isEqualTo(ExpressionErrorCode.FIELD_ARGUMENT_REQUIRED)
    assertThat(validate("count_of(sessions, notiz == 'kraulen')")).isNull()
  }
}
