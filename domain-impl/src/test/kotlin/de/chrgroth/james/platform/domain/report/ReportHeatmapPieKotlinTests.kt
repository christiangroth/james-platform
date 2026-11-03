package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.app.ScriptMetrics
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.CurrencyGranularity
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.ErrorPanelSpec
import de.chrgroth.james.platform.domain.model.app.HeatmapCellSpec
import de.chrgroth.james.platform.domain.model.app.HeatmapPanelSpec
import de.chrgroth.james.platform.domain.model.app.HeatmapThresholdSpec
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.PanelSpec
import de.chrgroth.james.platform.domain.model.app.PiePanelSpec
import de.chrgroth.james.platform.domain.model.app.PieSliceSpec
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.ReportDefinitionErrorCode
import de.chrgroth.james.platform.domain.model.app.ReportPanelType
import de.chrgroth.james.platform.domain.model.app.StatPanelSpec
import de.chrgroth.james.platform.domain.model.app.TablePanelSpec
import de.chrgroth.james.platform.domain.model.app.TimeGranularity
import de.chrgroth.james.platform.domain.model.app.UnitFamily
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.port.`in`.app.ReportScriptProblem
import de.chrgroth.james.platform.domain.port.`in`.app.ReportScriptResult
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** Heatmap and pie panels and the `kotlin` transformation step (Zählerstände, Arbeitszeiten, Journal). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportHeatmapPieKotlinTests {

  private val today = LocalDate.of(2026, 6, 15)
  private val seconds = PropertyUnit(UnitFamily.TIME, TimeGranularity.SECONDS, TimeGranularity.HOURS)

  private val arbeitstag = entity(
    "Arbeitstag",
    Property(PropertyId("a-datum"), "datum", PropertyType.DATE),
    Property(PropertyId("a-dauer"), "dauer", PropertyType.LONG, unit = seconds),
    Property(PropertyId("a-weg"), "verkehrsmittel", PropertyType.STRING),
  )

  private val eintrag = entity(
    "Eintrag",
    Property(PropertyId("j-datum"), "datum", PropertyType.DATE),
    Property(PropertyId("j-bewertung"), "bewertung", PropertyType.LONG),
  )

  private val ablesung = entity(
    "Ablesung",
    Property(PropertyId("z-datum"), "datum", PropertyType.DATE),
    Property(PropertyId("z-stand"), "stand", PropertyType.LONG),
  )

  private val entities = listOf(arbeitstag, eintrag, ablesung)

  private var created = Instant.parse("2026-01-01T00:00:00Z")

  private val data = listOf(
    data("a1", arbeitstag, "a-datum" to "2026-05-04", "a-dauer" to "28800", "a-weg" to "Rad"),
    data("a2", arbeitstag, "a-datum" to "2026-05-05", "a-dauer" to "30600", "a-weg" to "Bahn"),
    data("a3", arbeitstag, "a-datum" to "2026-05-06", "a-dauer" to "27000", "a-weg" to "Rad"),
    data("a4", arbeitstag, "a-datum" to "2026-06-01", "a-dauer" to "28800", "a-weg" to "Auto"),
    data("a5", arbeitstag, "a-datum" to "2026-06-01", "a-dauer" to "3600", "a-weg" to "Rad"),
    data("j1", eintrag, "j-datum" to "2026-06-01", "j-bewertung" to "4"),
    data("j2", eintrag, "j-datum" to "2026-06-02", "j-bewertung" to "2"),
    data("j3", eintrag, "j-datum" to "2026-06-02", "j-bewertung" to "4"),
    data("j4", eintrag, "j-datum" to "2025-12-31", "j-bewertung" to "5"),
    data("z1", ablesung, "z-datum" to "2026-01-01", "z-stand" to "1000"),
    data("z2", ablesung, "z-datum" to "2026-03-01", "z-stand" to "1600"),
  )

  private val scripts = ReportScriptService(ScriptMetrics(SimpleMeterRegistry()), 30_000L)

  private fun entity(name: String, vararg properties: Property) = EntityDefinition(id = EntityDefinitionId("e-$name"), name = name, properties = properties.toList())

  private fun data(id: String, entity: EntityDefinition, vararg values: Pair<String, String?>): AppData {
    created = created.plusSeconds(60)
    return AppData(
      id = AppDataId(id),
      userId = "user",
      installedAppId = InstalledAppId("inst-1"),
      appVersion = VersionNumber("1.0.0"),
      lastValidatedWithVersion = VersionNumber("1.0.0"),
      entityType = entity.id,
      objectVersion = 1,
      createdAt = created,
      lastChangedAt = created,
      data = values.toMap(),
    )
  }

  private fun panels(yaml: String, runner: ReportScriptRunner? = realScripts()): List<PanelSpec> {
    val result = ReportDefinitionParser.parse(yaml.trimIndent(), entities)
    assertThat(result.errors).isEmpty()
    val executor = ReportExecutor(entities, data, today, null, runner) { _, _ -> emptyMap() }
    return executor.execute("r", "Report", result.definition!!).tabs.single().rows.flatMap { it.panels }
  }

  private fun realScripts() = ReportScriptRunner { script, rows, config, label -> scripts.execute(script, rows, config, today, "Report", label) }

  private fun errors(yaml: String) = ReportDefinitionParser.parse(yaml.trimIndent(), entities).errors.map { it.code to it.path }

  // region pie

  @Test
  fun `pie shows the shares per group of an aggregate query, largest first`() {
    val pie = panels(
      """
      queries:
        wege: { entity: Arbeitstag, aggregate: { groupBy: verkehrsmittel, values: { tage: count } } }
      tabs:
        - name: T
          rows: [[ { pie: Verkehrsmittel, query: wege, donut: true } ]]
      """,
    ).single() as PiePanelSpec

    assertThat(pie.donut).isTrue()
    assertThat(pie.unit).isNull()
    assertThat(pie.slices).containsExactly(
      PieSliceSpec("Rad", 3.0, "3", "60 %"),
      PieSliceSpec("Auto", 1.0, "1", "20 %"),
      PieSliceSpec("Bahn", 1.0, "1", "20 %"),
    )
  }

  @Test
  fun `pie sums a plain query's field per category in the field's display unit`() {
    val pie = panels(
      """
      queries:
        tage: { entity: Arbeitstag, fields: [verkehrsmittel, dauer] }
      tabs:
        - name: T
          rows: [[ { pie: Stunden, query: tage, field: dauer, category: verkehrsmittel } ]]
      """,
    ).single() as PiePanelSpec

    assertThat(pie.unit).isEqualTo("h")
    assertThat(pie.slices.map { it.label to it.value }).containsExactly("Rad" to 16.5, "Bahn" to 8.5, "Auto" to 8.0)
  }

  @Test
  fun `pie needs a category and a numeric field`() {
    assertThat(
      errors(
        """
        queries:
          tage: { entity: Arbeitstag, fields: [verkehrsmittel, dauer] }
        tabs:
          - name: T
            rows: [[ { pie: A, query: tage, field: dauer }, { pie: B, query: tage, field: verkehrsmittel, category: verkehrsmittel }, { pie: C, query: tage } ]]
        """,
      ),
    ).containsExactly(
      ReportDefinitionErrorCode.PIE_CATEGORY_MISSING to "tabs[0].rows[0][0].query",
      ReportDefinitionErrorCode.FIELD_NOT_NUMERIC to "tabs[0].rows[0][1].field",
      ReportDefinitionErrorCode.REQUIRED_KEY_MISSING to "tabs[0].rows[0][2]",
      ReportDefinitionErrorCode.PIE_CATEGORY_MISSING to "tabs[0].rows[0][2].query",
    )
  }

  // endregion

  // region heatmap

  @Test
  fun `calendar heatmap reduces the rows per day over the query's range, with thresholds`() {
    val heatmap = panels(
      """
      queries:
        journal: { entity: Eintrag, timeField: datum, range: this_year, fields: [datum, bewertung] }
      tabs:
        - name: T
          rows: [[ { heatmap: Stimmung, query: journal, field: bewertung, reduce: avg, thresholds: [{ value: 1, color: red }, { value: 4, color: green }] } ]]
      """,
    ).single() as HeatmapPanelSpec

    assertThat(heatmap.calendar).isTrue()
    assertThat(heatmap.from).isEqualTo("2026-01-01")
    assertThat(heatmap.to).isEqualTo("2026-12-31")
    assertThat(heatmap.cells).containsExactly(HeatmapCellSpec("2026-06-01", null, 4.0, "4"), HeatmapCellSpec("2026-06-02", null, 3.0, "3"))
    assertThat(heatmap.min).isEqualTo(3.0)
    assertThat(heatmap.max).isEqualTo(4.0)
    assertThat(heatmap.thresholds).containsExactly(HeatmapThresholdSpec(1.0, "red"), HeatmapThresholdSpec(4.0, "green"))
  }

  @Test
  fun `calendar heatmap over a day bucket spans the days with data and plots values in the display unit`() {
    val heatmap = panels(
      """
      queries:
        stunden: { entity: Arbeitstag, timeField: datum, aggregate: { bucket: TAG, values: { dauer: { sum: dauer } } } }
      tabs:
        - name: T
          rows: [[ { heatmap: Arbeitszeit, query: stunden, thresholds: [{ value: 8h, color: green }] } ]]
      """,
    ).single() as HeatmapPanelSpec

    assertThat(heatmap.unit).isEqualTo("h")
    assertThat(heatmap.from).isEqualTo("2026-05-04")
    assertThat(heatmap.to).isEqualTo("2026-06-01")
    assertThat(heatmap.cells.map { it.x to it.value }).containsExactly("2026-05-04" to 8.0, "2026-05-05" to 8.5, "2026-05-06" to 7.5, "2026-06-01" to 9.0)
    assertThat(heatmap.thresholds).containsExactly(HeatmapThresholdSpec(8.0, "green"))
  }

  @Test
  fun `matrix heatmap shows one cell per bucket and group`() {
    val heatmap = panels(
      """
      queries:
        wege: { entity: Arbeitstag, timeField: datum, aggregate: { bucket: MONAT, groupBy: verkehrsmittel, values: { tage: count } } }
      tabs:
        - name: T
          rows: [[ { heatmap: Wege, query: wege, x: bucket, y: verkehrsmittel } ]]
      """,
    ).single() as HeatmapPanelSpec

    assertThat(heatmap.calendar).isFalse()
    assertThat(heatmap.xCategories).containsExactly("05/2026", "06/2026")
    assertThat(heatmap.yCategories).containsExactlyInAnyOrder("Rad", "Bahn", "Auto")
    assertThat(heatmap.cells.map { Triple(it.x, it.y, it.value) }).containsExactlyInAnyOrder(
      Triple("05/2026", "Rad", 2.0),
      Triple("05/2026", "Bahn", 1.0),
      Triple("06/2026", "Auto", 1.0),
      Triple("06/2026", "Rad", 1.0),
    )
  }

  @Test
  fun `heatmap validation - a calendar needs days, a matrix both axes`() {
    assertThat(
      errors(
        """
        queries:
          monate: { entity: Arbeitstag, timeField: datum, aggregate: { bucket: MONAT, values: { dauer: { sum: dauer } } } }
          tage: { entity: Arbeitstag, timeField: datum, fields: [datum, dauer, verkehrsmittel] }
        tabs:
          - name: T
            rows:
              - - { heatmap: A, query: monate }
                - { heatmap: B, query: tage, field: dauer, x: datum }
                - { heatmap: C, query: tage, field: dauer, thresholds: [{ value: viel, color: red }] }
                - { heatmap: D, query: tage, reduce: count }
                - { heatmap: E, query: tage, field: verkehrsmittel }
        """,
      ),
    ).containsExactly(
      ReportDefinitionErrorCode.HEATMAP_NEEDS_DAY to "tabs[0].rows[0][0].query",
      ReportDefinitionErrorCode.REQUIRED_KEY_MISSING to "tabs[0].rows[0][1]",
      ReportDefinitionErrorCode.INVALID_NUMBER to "tabs[0].rows[0][2].thresholds[0].value",
      ReportDefinitionErrorCode.FIELD_NOT_NUMERIC to "tabs[0].rows[0][4].field",
    )
  }

  // endregion

  // region kotlin step

  @Test
  fun `kotlin step computes rows the declarative steps cannot, e g a break-even date`() {
    val panels = panels(
      """
      queries:
        staende: { entity: Ablesung, timeField: datum, fields: [datum, stand], sort: [datum] }
      transforms:
        staende:
          - kotlin: |
              val first = rows.first()
              val last = rows.last()
              val days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(first["datum"]), java.time.LocalDate.parse(last["datum"]))
              val perDay = (last["stand"]!!.toDouble() - first["stand"]!!.toDouble()) / days
              val remaining = ((10_000 - last["stand"]!!.toDouble()) / perDay).toLong()
              listOf(mapOf("breakEven" to java.time.LocalDate.parse(last["datum"]).plusDays(remaining), "proTag" to perDay, "stand" to last["stand"]))
            fields: { breakEven: DATE, proTag: DOUBLE }
      tabs:
        - name: T
          rows: [[ { table: Break-even, query: staende, columns: [breakEven, proTag, stand] }, { stat: Pro Tag, query: staende, field: proTag } ]]
      """,
    )

    val table = panels[0] as TablePanelSpec
    assertThat(table.rows.single().map { it.text }).containsExactly("04.06.2028", "10,17", "1.600")
    assertThat((panels[1] as StatPanelSpec).value).isCloseTo(10.169, org.assertj.core.data.Offset.offset(0.001))
  }

  @Test
  fun `a failing kotlin step turns the panels of its query into an error, the rest of the report renders`() {
    val panels = panels(
      """
      queries:
        staende: { entity: Ablesung, timeField: datum, fields: [datum, stand] }
        journal: { entity: Eintrag, timeField: datum, fields: [datum, bewertung] }
      transforms:
        staende: [{ kotlin: "rows", fields: { x: LONG } }, { cumulative: stand }]
      tabs:
        - name: T
          rows: [[ { table: Stände, query: staende }, { timeseries: Stand, query: staende, field: stand }, { table: Journal, query: journal } ]]
      """,
      runner = { _, _, _, _ -> ReportScriptResult.Failure("Der Kotlin-Schritt wurde nach 500 ms abgebrochen (Zeitüberschreitung).") },
    )

    assertThat(panels[0]).isEqualTo(
      ErrorPanelSpec(ReportPanelType.TABLE, "Stände", "Abfrage „staende“: Der Kotlin-Schritt wurde nach 500 ms abgebrochen (Zeitüberschreitung)."),
    )
    assertThat(panels[1]).isInstanceOf(ErrorPanelSpec::class.java)
    assertThat((panels[2] as TablePanelSpec).rows).hasSize(4)
  }

  @Test
  fun `kotlin step validation - declared field types, reserved names and fields panels use`() {
    assertThat(
      errors(
        """
        queries:
          staende: { entity: Ablesung, timeField: datum, aggregate: { bucket: MONAT, values: { stand: { last: stand } } } }
        transforms:
          staende: [{ kotlin: "rows", fields: { a: TEXT, bucket: STRING, ersparnis: CENTS } }, { kotlin: " " }]
        tabs:
          - name: T
            rows: [[ { table: A, query: staende, columns: [ersparnis, b] } ]]
        """,
      ),
    ).containsExactly(
      ReportDefinitionErrorCode.INVALID_VALUE to "transforms.staende[0].fields.a",
      ReportDefinitionErrorCode.DUPLICATE_RESULT_FIELD to "transforms.staende[0].fields.bucket",
      ReportDefinitionErrorCode.REQUIRED_KEY_MISSING to "transforms.staende[1].kotlin",
      ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY to "tabs[0].rows[0][0].columns[1]",
    )
  }

  @Test
  fun `scriptErrors reports compile problems of kotlin steps at their key and line`() {
    val yaml = """
      queries:
        staende: { entity: Ablesung, fields: [stand] }
      transforms:
        staende:
          - { cumulative: stand }
          - kotlin: "unbekannt(rows)"
      tabs: []
    """.trimIndent()
    val definition = ReportDefinitionParser.parse(yaml, entities).definition!!

    val errors = ReportDefinitionParser.scriptErrors(yaml, definition) { script ->
      if ("unbekannt" in script) ReportScriptProblem("Unresolved reference 'unbekannt'.", 1) else null
    }

    assertThat(errors.map { Triple(it.code, it.path, it.line) }).containsExactly(Triple(ReportDefinitionErrorCode.KOTLIN_SCRIPT_INVALID, "transforms.staende[1].kotlin", 6))
    assertThat(errors.single().args).containsExactly("Unresolved reference 'unbekannt'.", "1")
  }

  // endregion

  // region arc42 example

  @Test
  fun `the arc42 example of heatmap, pie and kotlin step is valid, compiles and renders without errors`() {
    val arc42 = File("../docs/arc42/arc42.md").readLines()
    val start = arc42.indexOfFirst { it.startsWith("  Heatmap, pie and the Kotlin step, as used by the ports") }
    val from = arc42.withIndex().first { (i, line) -> i > start && line == "  ```yaml" }.index + 1
    val to = arc42.withIndex().first { (i, line) -> i > from && line == "  ```" }.index
    val yaml = arc42.subList(from, to).joinToString("\n") { it.removePrefix("  ") }

    val hours = PropertyUnit(UnitFamily.TIME, TimeGranularity.SECONDS, TimeGranularity.HOURS)
    val cents = PropertyUnit(UnitFamily.CURRENCY, CurrencyGranularity.CENTS, CurrencyGranularity.EUROS)
    val zaehlerstand = entity(
      "Zaehlerstand",
      Property(PropertyId("x-datum"), "datum", PropertyType.DATE),
      Property(PropertyId("x-zaehler"), "zaehler", PropertyType.STRING),
      Property(PropertyId("x-stand"), "stand", PropertyType.LONG),
    )
    val tag = entity(
      "Arbeitstag",
      Property(PropertyId("t-datum"), "datum", PropertyType.DATE),
      Property(PropertyId("t-dauer"), "dauer", PropertyType.LONG, unit = hours),
      Property(PropertyId("t-weg"), "verkehrsmittel", PropertyType.STRING),
    )
    val modell = entity(
      "Arbeitszeitmodell",
      Property(PropertyId("m-ab"), "gueltigAb", PropertyType.DATE),
      Property(PropertyId("m-soll"), "sollProTag", PropertyType.LONG, unit = hours),
    )
    val config = entity(
      "Einstellungen",
      Property(PropertyId("c-anschaffung"), "anschaffung", PropertyType.LONG, unit = cents),
      Property(PropertyId("c-preis"), "preisProKwh", PropertyType.LONG, unit = cents),
    ).copy(configuration = true)
    val all = listOf(zaehlerstand, tag, modell, eintrag, config)
    val rows = listOf(
      data("p1", zaehlerstand, "x-datum" to "2026-01-01", "x-zaehler" to "PV", "x-stand" to "0"),
      data("p2", zaehlerstand, "x-datum" to "2026-03-01", "x-zaehler" to "PV", "x-stand" to "600"),
      data("p3", zaehlerstand, "x-datum" to "2026-06-01", "x-zaehler" to "PV", "x-stand" to "1500"),
      data("t1", tag, "t-datum" to "2026-06-01", "t-dauer" to "28800", "t-weg" to "Rad"),
      data("m1", modell, "m-ab" to "2025-01-01", "m-soll" to "28800"),
      data("j1", eintrag, "j-datum" to "2026-06-01", "j-bewertung" to "4"),
    )
    val configuration = data("c1", config, "c-anschaffung" to "1000000", "c-preis" to "30")

    val result = ReportDefinitionParser.parse(yaml, all)
    assertThat(result.errors).isEmpty()
    assertThat(ReportDefinitionParser.scriptErrors(yaml, result.definition!!, scripts::compileProblem)).isEmpty()

    val panels = ReportExecutor(all, rows + configuration, today, configuration, realScripts()) { _, _ -> emptyMap() }
      .execute("r", "Beispiel", result.definition).tabs.single().rows.flatMap { it.panels }
    assertThat(panels).noneMatch { it is ErrorPanelSpec }
    val breakEven = panels.filterIsInstance<TablePanelSpec>().first { it.title == "Break-even" }
    assertThat(breakEven.rows.last().last().text).isNotEmpty()
  }

  // endregion
}
