package de.chrgroth.james.platform.domain.expression

import de.chrgroth.james.platform.domain.error.ExpressionErrorCode
import de.chrgroth.james.platform.domain.model.app.ComputedProperty
import de.chrgroth.james.platform.domain.model.app.ComputedPropertyId
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.encodeListValue
import de.chrgroth.james.platform.domain.model.app.encodeObjectValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** `valid_at(list, key, at, value)`: the value of the item valid at a point, over a LIST of OBJECTs of the object or of the configuration. */
class ValidAtFunctionTests {

  private val ab = Property(id = PropertyId("c-ab"), name = "ab", type = PropertyType.DATE)
  private val sollzeit = Property(id = PropertyId("c-soll"), name = "sollzeit_pro_tag", type = PropertyType.DOUBLE)
  private val arbeitstage = Property(id = PropertyId("c-tage"), name = "arbeitstage_pro_woche", type = PropertyType.LONG)
  private val sollzeiten = Property(
    id = PropertyId("c-sollzeiten"),
    name = "sollzeiten",
    type = PropertyType.LIST,
    listItemType = PropertyType.OBJECT,
    nestedProperties = listOf(ab, sollzeit, arbeitstage),
  )
  private val config = EntityDefinition(id = EntityDefinitionId("e-config"), name = "Einstellungen", configuration = true, properties = listOf(sollzeiten))

  private val datum = Property(id = PropertyId("p-datum"), name = "datum", type = PropertyType.DATE)
  private val stufen = Property(
    id = PropertyId("p-stufen"),
    name = "stufen",
    type = PropertyType.LIST,
    listItemType = PropertyType.OBJECT,
    nestedProperties = listOf(Property(id = PropertyId("p-ab"), name = "ab", type = PropertyType.DATE), Property(id = PropertyId("p-wert"), name = "wert", type = PropertyType.STRING)),
  )
  private val entity = EntityDefinition(id = EntityDefinitionId("e-tag"), name = "Arbeitstag", properties = listOf(datum, stufen))

  private fun period(from: String?, hours: String?, days: String? = "5") = encodeObjectValue(mapOf("c-ab" to from, "c-soll" to hours, "c-tage" to days))

  // Unsorted on purpose: the item with the greatest key not after the point wins, whatever the list order.
  private val configValues = mapOf(
    "c-sollzeiten" to encodeListValue(listOf(period("2026-07-01", "6", "4"), period("2025-01-01", "8"), period(null, "1"), period("2026-07-01", "7", "4"))),
  )

  private fun eval(source: String, on: String, config: Map<String, String?> = configValues): ExpressionValue? = Expression.parse(source).evaluate(
    EntityExpressionContext(entity.properties, dataOn(on), config, this.config, LocalDateTime.of(2026, 11, 4, 12, 0)),
  )

  private fun dataOn(date: String) = mapOf(
    "p-datum" to date,
    "p-stufen" to encodeListValue(listOf(encodeObjectValue(mapOf("p-ab" to "2026-01-01", "p-wert" to "A")), encodeObjectValue(mapOf("p-ab" to "2026-06-01", "p-wert" to "B")))),
  )

  private fun validate(source: String, type: PropertyType = PropertyType.DOUBLE) =
    EntityExpressions.validateComputed(entity, ComputedProperty(ComputedPropertyId("c-1"), "soll", type, source), source, config)

  @Test
  fun `picks the value of the item with the greatest key not after the point, the later one on equal keys`() {
    assertThat(eval("valid_at(config.sollzeiten, ab, datum, sollzeit_pro_tag)", "2026-06-30")).isEqualTo(ExpressionValue.Num(8.0))
    assertThat(eval("valid_at(config.sollzeiten, ab, datum, sollzeit_pro_tag)", "2026-07-01")).isEqualTo(ExpressionValue.Num(7.0))
    assertThat(eval("valid_at(config.sollzeiten, ab, datum, arbeitstage_pro_woche)", "2026-11-04")).isEqualTo(ExpressionValue.Num(4.0))
    assertThat(eval("valid_at(config.sollzeiten, ab, datum, ab)", "2026-11-04")).isEqualTo(ExpressionValue.Date(LocalDate.of(2026, 7, 1)))
  }

  @Test
  fun `has no value before the first item, without a point or without items, and skips items without key`() {
    assertThat(eval("valid_at(config.sollzeiten, ab, datum, sollzeit_pro_tag)", "2024-12-31")).isNull()
    assertThat(eval("valid_at(config.sollzeiten, ab, null, sollzeit_pro_tag)", "2026-11-04")).isNull()
    assertThat(eval("valid_at(config.sollzeiten, ab, datum, sollzeit_pro_tag)", "2026-11-04", emptyMap())).isNull()
    assertThat(eval("coalesce(valid_at(config.sollzeiten, ab, datum, sollzeit_pro_tag), 0)", "2024-12-31")).isEqualTo(ExpressionValue.Num(0.0))
  }

  @Test
  fun `works on the object's own LIST and with the point computed from the object`() {
    assertThat(eval("valid_at(stufen, ab, datum, wert)", "2026-05-31")).isEqualTo(ExpressionValue.Text("A"))
    assertThat(eval("valid_at(stufen, ab, add_days(datum, 1), wert)", "2026-05-31")).isEqualTo(ExpressionValue.Text("B"))
  }

  @Test
  fun `reads the point outside the list and the key and value per item`() {
    val expression = Expression.parse("valid_at(config.sollzeiten, ab, datum, sollzeit_pro_tag * config.faktor)")

    assertThat(expression.identifiers).containsExactlyInAnyOrder("config.sollzeiten", "datum", "config.faktor")
    assertThat(expression.itemIdentifiers).isEqualTo(mapOf("config.sollzeiten" to setOf("ab", "sollzeit_pro_tag")))
  }

  @Test
  fun `validation types the result by the value and checks the key against the point`() {
    assertThat(validate("valid_at(config.sollzeiten, ab, datum, sollzeit_pro_tag) * 60")).isNull()
    assertThat(validate("valid_at(stufen, ab, datum, wert)", PropertyType.STRING)).isNull()
    assertThat(validate("valid_at(stufen, ab, datum, ab)", PropertyType.LONG)?.reason).isEqualTo(ExpressionErrorCode.RESULT_TYPE)
    assertThat(validate("valid_at(config.sollzeiten, sollzeit_pro_tag, datum, ab)", PropertyType.DATE)?.reason).isEqualTo(ExpressionErrorCode.INCOMPARABLE)
    assertThat(validate("valid_at(config.fehlt, ab, datum, sollzeit_pro_tag)")?.reason).isEqualTo(ExpressionErrorCode.UNKNOWN_CONFIG_FIELD)
    assertThat(validate("valid_at(datum, ab, datum, sollzeit_pro_tag)")?.reason).isEqualTo(ExpressionErrorCode.LIST_FIELD_REQUIRED)
    assertThat(validate("valid_at(config.sollzeiten, beginn, datum, sollzeit_pro_tag)")?.reason).isEqualTo(ExpressionErrorCode.UNKNOWN_ITEM_FIELD)
  }

  @Test
  fun `the other list functions read configuration lists too`() {
    assertThat(eval("count_of(config.sollzeiten)", "2026-11-04")).isEqualTo(ExpressionValue.Num(4.0))
    assertThat(eval("max_of(config.sollzeiten, sollzeit_pro_tag)", "2026-11-04")).isEqualTo(ExpressionValue.Num(8.0))
  }
}
