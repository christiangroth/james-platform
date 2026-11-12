package de.chrgroth.james.platform.domain.expression

import de.chrgroth.james.platform.domain.error.ExpressionErrorCode
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.ComputedProperty
import de.chrgroth.james.platform.domain.model.app.ComputedPropertyId
import de.chrgroth.james.platform.domain.model.app.DisplayTextResolver
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.TimeGranularity
import de.chrgroth.james.platform.domain.model.app.UnitFamily
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class DisplayTextExpressionTests {

  private val start = Property(id = PropertyId("p-start"), name = "start", type = PropertyType.DATE)
  private val ende = Property(id = PropertyId("p-ende"), name = "ende", type = PropertyType.DATE)
  private val tage = Property(id = PropertyId("p-tage"), name = "tage", type = PropertyType.DOUBLE)
  private val kommentar = Property(id = PropertyId("p-kommentar"), name = "kommentar", type = PropertyType.STRING, nullable = true)
  private val beginn = Property(id = PropertyId("p-beginn"), name = "beginn", type = PropertyType.TIME, nullable = true)
  private val pause = Property(id = PropertyId("p-pause"), name = "pause", type = PropertyType.LONG)
  private val netto = ComputedProperty(
    id = ComputedPropertyId("c-netto"),
    name = "netto",
    type = PropertyType.LONG,
    expression = "480 - pause",
    unit = PropertyUnit(UnitFamily.TIME, TimeGranularity.MINUTES, TimeGranularity.MINUTES),
  )

  private fun entity(template: String) = EntityDefinition(
    id = EntityDefinitionId("e-1"),
    name = "Urlaub",
    displayText = template,
    properties = listOf(start, ende, tage, kommentar, beginn, pause),
    computedProperties = listOf(netto),
  )

  private fun render(template: String, data: Map<String, String?>): String {
    val entity = entity(template)
    return DisplayTextResolver(mapOf(entity.id.value to entity), emptyList(), DisplayTextExpressions).displayText(entity, "id-1", data)
  }

  private val urlaubTemplate = "\${kommentar} \${format(start, \"dd.MM\")}-\${format(ende, \"dd.MM\")} ({tage} Tage)"
  private val full = mapOf("p-start" to "2026-08-03", "p-ende" to "2026-08-14", "p-tage" to "9.5", "p-kommentar" to "Sommer", "p-pause" to "30")

  @Test
  fun `an expression placeholder formats dates with the pattern and keeps property placeholders working`() {
    assertThat(render(urlaubTemplate, full)).isEqualTo("Sommer 03.08-14.08 (9,5 Tage)")
  }

  @Test
  fun `an empty optional value leaves no leading text or space`() {
    assertThat(render(urlaubTemplate, full + ("p-kommentar" to null))).isEqualTo("03.08-14.08 (9,5 Tage)")
    assertThat(render(urlaubTemplate, full + ("p-kommentar" to ""))).isEqualTo("03.08-14.08 (9,5 Tage)")
  }

  @Test
  fun `a placeholder naming just a property is formatted like the property placeholder`() {
    assertThat(render("\${start} \${beginn} \${pause}", full + ("p-beginn" to "06:00"))).isEqualTo("03.08.2026 06:00 30")
    assertThat(render("\${start} \${beginn} \${pause}", full)).isEqualTo("03.08.2026 30")
  }

  @Test
  fun `computed properties are readable, a bare one is formatted with its unit`() {
    assertThat(render("\${netto}", full)).isEqualTo("7h 30min")
    assertThat(render("\${if(netto > 400, 'lang', 'kurz')}", full)).isEqualTo("lang")
    assertThat(render("\${netto / 60}", full)).isEqualTo("7,5")
  }

  @Test
  fun `expressions with conditions, coalesce and concat give text`() {
    assertThat(render("\${coalesce(kommentar, 'ohne')}", full + ("p-kommentar" to null))).isEqualTo("ohne")
    assertThat(render("\${if(present(kommentar), concat('(', kommentar, ')'), '')} \${format(start, 'yyyy')}", full)).isEqualTo("(Sommer) 2026")
    assertThat(render("\${start >= end_of_month(start)}", full)).isEqualTo("nein")
  }

  @Test
  fun `no value or broken data renders empty, never throws`() {
    assertThat(render("A \${format(start, 'dd.MM')} B", full + ("p-start" to null))).isEqualTo("A B")
    assertThat(render("A \${format(start, 'dd.MM')} B", full + ("p-start" to "kaputt"))).isEqualTo("A B")
    assertThat(render("A \${unknown + 1} B", full)).isEqualTo("A B")
    assertThat(render("A \${1 + } B", full)).isEqualTo("A B")
    assertThat(render("\${format(beginn, 'dd.MM')}", full + ("p-beginn" to "06:00")).trim()).isEqualTo("Urlaub id-1 []")
  }

  @Test
  fun `a template whose placeholders all are empty falls back to the reference text`() {
    assertThat(render("\${kommentar}", full + ("p-kommentar" to null))).isEqualTo("Urlaub id-1 []")
  }

  @Test
  fun `plain templates are unchanged, also with dollar signs and double blanks`() {
    assertThat(render("{start} – {ende}  ({tage} Tage)", full)).isEqualTo("03.08.2026 – 14.08.2026  (9,5 Tage)")
    assertThat(render("\$ {tage} {id}", full)).isEqualTo("$ 9,5 id-1")
    assertThat(render("{unknown} {start}", full)).isEqualTo("<?> 03.08.2026")
  }

  @Test
  fun `an expression may contain a closing brace in a text`() {
    assertThat(render("\${concat('a}b', '{c')}", full)).isEqualTo("a}b{c")
  }

  @Test
  fun `no evaluator renders expression placeholders empty`() {
    val entity = entity("x \${year(start)} y")
    assertThat(DisplayTextResolver(emptyMap(), emptyList()).displayText(entity, "id-1", full)).isEqualTo("x y")
  }

  @Test
  fun `references are shown as display text in a bare placeholder`() {
    val ref = Property(id = PropertyId("p-ref"), name = "arbeitgeber", type = PropertyType.REF)
    val target = EntityDefinition(id = EntityDefinitionId("e-2"), name = "Arbeitgeber", displayText = "{name}", properties = listOf(Property(PropertyId("p-name"), "name", PropertyType.STRING)))
    val source = entity("\${arbeitgeber}").copy(properties = listOf(ref))
    val targetData = AppData(
      id = AppDataId("t-1"), userId = "u", installedAppId = InstalledAppId("i"), appVersion = VersionNumber("1.0.0"), lastValidatedWithVersion = VersionNumber("1.0.0"),
      entityType = target.id, objectVersion = 1, createdAt = Instant.EPOCH, lastChangedAt = Instant.EPOCH, data = mapOf("p-name" to "ACME"),
    )

    val resolver = DisplayTextResolver(listOf(source, target).associateBy { it.id.value }, listOf(targetData), DisplayTextExpressions)

    assertThat(resolver.displayText(source, "x", mapOf("p-ref" to "t-1"))).isEqualTo("ACME")
  }

  // region validation

  @Test
  fun `a valid template is accepted, also with optional properties`() {
    assertThat(DisplayTextExpressions.validate(entity(urlaubTemplate), urlaubTemplate)).isNull()
    assertThat(DisplayTextExpressions.validate(entity("{start}"), "{start}")).isNull()
  }

  @Test
  fun `an unknown field, function or syntax error is rejected with the reason`() {
    val e = entity("")
    assertThat(DisplayTextExpressions.validate(e, "\${nope}")?.reason).isEqualTo(ExpressionErrorCode.UNKNOWN_FIELD)
    assertThat(DisplayTextExpressions.validate(e, "\${nope(start)}")?.reason).isEqualTo(ExpressionErrorCode.UNKNOWN_FUNCTION)
    assertThat(DisplayTextExpressions.validate(e, "\${1 + }")?.reason).isEqualTo(ExpressionErrorCode.SYNTAX)
    assertThat(DisplayTextExpressions.validate(e, "a \${format(start, 'dd.MM'")?.reason).isEqualTo(ExpressionErrorCode.SYNTAX)
    assertThat(DisplayTextExpressions.validate(e, "\${format(start, 'qqqqqq')}")?.reason).isEqualTo(ExpressionErrorCode.SYNTAX)
    assertThat(DisplayTextExpressions.validate(e, "\${format(tage, 'dd')}")?.reason).isEqualTo(ExpressionErrorCode.ARGUMENT_TYPE)
    assertThat(DisplayTextExpressions.validate(e, "\${last(start)}")?.reason).isEqualTo(ExpressionErrorCode.FUNCTION_NOT_AVAILABLE)
    assertThat(DisplayTextExpressions.validate(e, "\${config.x}")?.reason).isEqualTo(ExpressionErrorCode.UNKNOWN_CONFIG_FIELD)
  }

  // endregion
}
