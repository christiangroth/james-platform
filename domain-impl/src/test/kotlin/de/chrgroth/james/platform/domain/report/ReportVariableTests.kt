package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.ReportDefinitionErrorCode
import de.chrgroth.james.platform.domain.model.app.ReportView
import de.chrgroth.james.platform.domain.model.app.StatPanelSpec
import de.chrgroth.james.platform.domain.model.app.TablePanelSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Report variables: time range and select controls, referenced as `$name` in query ranges and filter values. */
class ReportVariableTests {

  private val today = LocalDate.of(2026, 6, 15)

  private val data = listOf(
    ReportFixtures.radData("r-1", "Gravel"),
    ReportFixtures.radData("r-2", "Rennrad"),
    ReportFixtures.fahrtData("f-1", "2025-03-05", 10_000, "r-1"),
    ReportFixtures.fahrtData("f-2", "2026-05-20", 20_000, "r-2"),
    ReportFixtures.fahrtData("f-3", "2026-06-02", 5_000, "r-1"),
  )

  private fun view(yaml: String, selections: Map<String, String> = emptyMap()): ReportView {
    val result = ReportDefinitionParser.parse(yaml.trimIndent(), ReportFixtures.entities)
    assertThat(result.errors).isEmpty()
    return ReportExecutor(ReportFixtures.entities, data, today, null) { _, _ -> emptyMap() }.execute("r", "Variablen", result.definition!!, selections)
  }

  private fun errors(yaml: String) = ReportDefinitionParser.parse(yaml.trimIndent(), ReportFixtures.entities).errors.map { it.code to it.path }

  private fun ReportView.stat(): Double? = (tabs.single().rows.single().panels.single() as StatPanelSpec).value

  private val timeRangeReport = """
    variables:
      zeitraum: { type: timeRange, label: Zeitraum, default: this_month, options: [this_month, last_month, this_year, all] }
    queries:
      km: { entity: Fahrt, timeField: datum, range: ${'$'}zeitraum, aggregate: { sum: distanz } }
    tabs:
      - name: T
        rows: [[{ stat: Km, query: km }]]
    """

  @Test
  fun `a time range variable restricts queries to the selected period`() {
    val default = view(timeRangeReport)
    assertThat(default.stat()).isEqualTo(5_000.0)
    assertThat(default.variables.single().selected).isEqualTo("this_month")
    assertThat(default.variables.single().label).isEqualTo("Zeitraum")
    assertThat(default.variables.single().options.map { it.value to it.label }).containsExactly(
      "this_month" to "Dieser Monat",
      "last_month" to "Letzter Monat",
      "this_year" to "Dieses Jahr",
      "all" to "Gesamter Zeitraum",
    )

    assertThat(view(timeRangeReport, mapOf("zeitraum" to "last_month")).stat()).isEqualTo(20_000.0)
    assertThat(view(timeRangeReport, mapOf("zeitraum" to "all")).stat()).isEqualTo(35_000.0)
    // Not an option: the default applies.
    assertThat(view(timeRangeReport, mapOf("zeitraum" to "last_7_days")).variables.single().selected).isEqualTo("this_month")
  }

  @Test
  fun `a select variable over a year bucket offers the years and restricts ranges to the selected one`() {
    val yaml = """
      variables:
        jahr: { type: select, query: jahre, field: bucket, default: last }
      queries:
        jahre: { entity: Fahrt, timeField: datum, aggregate: { bucket: JAHR, values: { n: count } } }
        km: { entity: Fahrt, timeField: datum, range: ${'$'}jahr, aggregate: { sum: distanz } }
      tabs:
        - name: T
          rows: [[{ stat: Km, query: km }]]
      """

    val default = view(yaml)
    assertThat(default.variables.single().options.map { it.value }).containsExactly("2025", "2026")
    assertThat(default.variables.single().selected).isEqualTo("2026")
    assertThat(default.stat()).isEqualTo(25_000.0)
    assertThat(view(yaml, mapOf("jahr" to "2025")).stat()).isEqualTo(10_000.0)
  }

  @Test
  fun `a select variable in a filter value filters by the selection, all drops the rule`() {
    val yaml = """
      variables:
        rad: { type: select, label: Rad, query: raeder, field: rad, all: true, default: all }
      queries:
        raeder: { entity: Fahrt, aggregate: { groupBy: rad, values: { n: count } } }
        fahrten: { entity: Fahrt, fields: [datum, distanz], filter: [{ field: rad, op: EQUALS, value: ${'$'}rad }] }
      tabs:
        - name: T
          rows: [[{ table: Fahrten, query: fahrten }]]
      """

    val all = view(yaml)
    assertThat(all.variables.single().options.map { it.value to it.label }).containsExactly("" to "Alle", "r-1" to "Gravel", "r-2" to "Rennrad")
    assertThat(all.variables.single().selected).isEmpty()
    assertThat((all.tabs.single().rows.single().panels.single() as TablePanelSpec).totalRows).isEqualTo(3)

    val gravel = view(yaml, mapOf("rad" to "r-1"))
    assertThat((gravel.tabs.single().rows.single().panels.single() as TablePanelSpec).totalRows).isEqualTo(2)
  }

  @Test
  fun `invalid variables and references are reported`() {
    assertThat(
      errors(
        """
        variables:
          zeitraum: { type: timeRange, default: gestern }
          rad: { type: select, query: raeder, field: rad }
          abhaengig: { type: select, query: gefiltert, field: rad }
          "1x": { type: timeRange }
          art: { type: liste }
          ohneAlle: { type: select, query: raeder, field: rad, default: all }
        queries:
          raeder: { entity: Fahrt, aggregate: { groupBy: rad, values: { n: count } } }
          gefiltert: { entity: Fahrt, timeField: datum, range: ${'$'}zeitraum, fields: [rad] }
          a: { entity: Fahrt, timeField: datum, range: ${'$'}rad }
          b: { entity: Fahrt, filter: [{ field: rad, op: EQUALS, value: ${'$'}zeitraum }] }
          c: { entity: Fahrt, filter: [{ field: rad, op: EQUALS, value: ${'$'}unbekannt }] }
        tabs: []
        """,
      ),
    ).containsExactly(
      ReportDefinitionErrorCode.INVALID_VALUE to "variables.zeitraum.default",
      ReportDefinitionErrorCode.VARIABLE_NAME_INVALID to "variables.1x",
      ReportDefinitionErrorCode.INVALID_VALUE to "variables.art.type",
      ReportDefinitionErrorCode.KEY_CONFLICT to "variables.ohneAlle.default",
      ReportDefinitionErrorCode.VARIABLE_OPTIONS_QUERY_INVALID to "variables.abhaengig.query",
      ReportDefinitionErrorCode.VARIABLE_TYPE_MISMATCH to "queries.a.range",
      ReportDefinitionErrorCode.VARIABLE_TYPE_MISMATCH to "queries.b.filter[0].value",
      ReportDefinitionErrorCode.VARIABLE_NOT_FOUND to "queries.c.filter[0].value",
    )
  }
}
