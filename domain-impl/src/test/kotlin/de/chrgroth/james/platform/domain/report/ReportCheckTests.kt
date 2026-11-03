package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.DistanceGranularity
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.ReportDefinitionErrorCode
import de.chrgroth.james.platform.domain.model.app.ReportView
import de.chrgroth.james.platform.domain.model.app.UnitFamily
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.model.app.WarningsPanelSpec
import de.chrgroth.james.platform.domain.model.app.encodeListValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/** Checks: gap detection with calendars, value and required rules, warnings panels and the tab counter. */
class ReportCheckTests {

  // Monday, 15.06.2026.
  private val today = LocalDate.of(2026, 6, 15)

  private val settings = EntityDefinition(
    id = EntityDefinitionId("e-settings"),
    name = "Einstellungen",
    configuration = true,
    properties = listOf(
      Property(PropertyId("p-feiertage"), "feiertage", PropertyType.LIST, listItemType = PropertyType.DATE),
      Property(PropertyId("p-max"), "maxStrecke", PropertyType.LONG, unit = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.METERS, DistanceGranularity.KILOMETERS)),
      Property(PropertyId("p-name"), "name", PropertyType.STRING),
    ),
  )

  private val urlaub = EntityDefinition(
    id = EntityDefinitionId("e-urlaub"),
    name = "Urlaub",
    properties = listOf(Property(PropertyId("p-von"), "von", PropertyType.DATE), Property(PropertyId("p-bis"), "bis", PropertyType.DATE)),
  )

  private val entities = ReportFixtures.entities + settings + urlaub

  private val config = appData("cfg", settings, mapOf("p-feiertage" to encodeListValue(listOf("2026-06-04")), "p-max" to "25000", "p-name" to "x"))

  private val fahrten = listOf(
    ReportFixtures.fahrtData("f-1", "2026-01-10", 10_000, null, gewicht = 81.0),
    ReportFixtures.fahrtData("f-2", "2026-03-05", 20_000, null, gewicht = 80.0),
    ReportFixtures.fahrtData("f-3", "2026-03-20", 5_000, null, gewicht = 79.0),
    ReportFixtures.fahrtData("f-4", "2026-06-01", 30_000, null, gewicht = null),
    ReportFixtures.fahrtData("f-5", "2026-06-02", 12_000, null, gewicht = 78.0),
  )

  private fun appData(id: String, entity: EntityDefinition, data: Map<String, String?>) = AppData(
    id = AppDataId(id),
    userId = "user",
    installedAppId = InstalledAppId("inst-1"),
    appVersion = VersionNumber("1.0.0"),
    lastValidatedWithVersion = VersionNumber("1.0.0"),
    entityType = entity.id,
    objectVersion = 1,
    createdAt = Instant.EPOCH,
    lastChangedAt = Instant.EPOCH,
    data = data,
  )

  private fun view(yaml: String, data: List<AppData> = fahrten): ReportView {
    val result = ReportDefinitionParser.parse(yaml.trimIndent(), entities)
    assertThat(result.errors).isEmpty()
    val executor = ReportExecutor(entities, data + config, today, config) { _, _ -> emptyMap() }
    return executor.execute("r", "Checks", result.definition!!)
  }

  private fun warnings(view: ReportView): WarningsPanelSpec = view.tabs.flatMap { it.rows }.flatMap { it.panels }.filterIsInstance<WarningsPanelSpec>().single()

  private fun errors(yaml: String) = ReportDefinitionParser.parse(yaml.trimIndent(), entities).errors.map { it.code to it.path }

  @Test
  fun `a monthly gap check reports missing months newest first and counts them on the tab`() {
    val view = view(
      """
      queries:
        fahrten: { entity: Fahrt, timeField: datum }
      checks:
        monatlich: { gap: fahrten, every: MONAT, message: "Keine Fahrt im {period}" }
      tabs:
        - name: Prüfungen
          rows: [[ { warnings: Hinweise } ]]
      """,
    )

    val panel = warnings(view)
    assertThat(panel.warnings.map { it.label }).containsExactly("05/2026", "04/2026", "02/2026")
    assertThat(panel.warnings.first().message).isEqualTo("Keine Fahrt im 05/2026")
    assertThat(panel.warnings.first().severity).isEqualTo("warning")
    assertThat(panel.total).isEqualTo(3)
    assertThat(view.tabs.single().warnings).isEqualTo(3)
  }

  @Test
  fun `the current period is only expected with current`() {
    val yaml = { current: Boolean ->
      """
      queries:
        fahrten: { entity: Fahrt, timeField: datum, range: { from: 2026-06-01 } }
      checks:
        woche: { gap: fahrten, every: WOCHE, current: $current }
      tabs:
        - name: P
          rows: [[ { warnings: Hinweise } ]]
      """
    }

    assertThat(warnings(view(yaml(false))).warnings.map { it.label }).containsExactly("KW 24/2026")
    assertThat(warnings(view(yaml(true))).warnings.map { it.label }).containsExactly("KW 25/2026", "KW 24/2026")
  }

  @Test
  fun `workday gaps skip weekends and dates of configuration and query calendars`() {
    val data = fahrten + appData("u-1", urlaub, mapOf("p-von" to "2026-06-08", "p-bis" to "2026-06-10")) +
      ReportFixtures.konzertData("k-1", "2026-06-05", null, "Hamburg", 1000)
    val view = view(
      """
      queries:
        fahrten: { entity: Fahrt, timeField: datum, range: { from: 2026-06-01, to: 2026-06-12 } }
        urlaub: { entity: Urlaub }
        konzerte: { entity: Konzert, timeField: datum }
      checks:
        werktage:
          gap: fahrten
          every: WERKTAG
          exclude: [config.feiertage, { query: urlaub, field: von, to: bis }, { query: konzerte }]
          severity: error
      tabs:
        - name: P
          rows: [[ { warnings: Hinweise } ]]
      """,
      data,
    )

    val panel = warnings(view)
    assertThat(panel.warnings.map { it.label }).containsExactly("12.06.2026", "11.06.2026", "03.06.2026")
    assertThat(panel.warnings.map { it.message }.distinct()).containsExactly("Kein Eintrag")
    assertThat(panel.warnings.map { it.severity }.distinct()).containsExactly("error")
  }

  @Test
  fun `a week whose days are all excluded is not expected`() {
    val data = fahrten + appData("u-1", urlaub, mapOf("p-von" to "2026-06-06", "p-bis" to "2026-06-14"))
    val view = view(
      """
      queries:
        fahrten: { entity: Fahrt, timeField: datum, range: { from: 2026-06-01 } }
        urlaub: { entity: Urlaub }
      checks:
        woche: { gap: fahrten, every: WOCHE, exclude: { query: urlaub, field: von, to: bis } }
      tabs:
        - name: P
          rows: [[ { warnings: Hinweise } ]]
      """,
      data,
    )

    assertThat(warnings(view).warnings).isEmpty()
  }

  @Test
  fun `value rules compare a field or an expression with bounds incl unit texts and configuration values`() {
    val view = view(
      """
      queries:
        fahrten: { entity: Fahrt, timeField: datum, fields: [datum, distanz, gewicht] }
      checks:
        weit: { rule: fahrten, field: distanz, max: config.maxStrecke, message: "Zu weit am {datum}: {value} (max. {max})" }
        kurz: { rule: fahrten, expr: "distanz / 1000", min: 6 }
        band: { rule: fahrten, field: distanz, min: 6km, max: 25km }
      tabs:
        - name: Weit
          rows: [[ { warnings: Weit, checks: [weit] } ]]
        - name: Kurz
          rows: [[ { warnings: Kurz, checks: [kurz] }, { warnings: Alle } ]]
      """,
    )

    val weit = view.tabs[0].rows.single().panels.single() as WarningsPanelSpec
    assertThat(weit.warnings.single().label).isEqualTo("01.06.2026")
    assertThat(weit.warnings.single().message).startsWith("Zu weit am 01.06.2026: 30").contains("max. 25")
    val kurz = view.tabs[1].rows.single().panels[0] as WarningsPanelSpec
    assertThat(kurz.warnings.single().message).isEqualTo("5 ist kleiner als 6")
    val all = view.tabs[1].rows.single().panels[1] as WarningsPanelSpec
    assertThat(all.warnings.map { it.check }).containsExactly("weit", "kurz", "band", "band")
    assertThat(all.warnings.last().message).contains("liegt nicht zwischen")
    // Each check counts once per tab, even when two panels show it.
    assertThat(view.tabs[1].warnings).isEqualTo(4)
  }

  @Test
  fun `required rules report missing values of rows having the when fields`() {
    val view = view(
      """
      queries:
        fahrten: { entity: Fahrt, timeField: datum, fields: [datum, distanz, gewicht] }
      checks:
        gewicht: { rule: fahrten, required: gewicht, when: [distanz] }
      tabs:
        - name: P
          rows: [[ { warnings: Hinweise } ]]
      """,
    )

    val warning = warnings(view).warnings.single()
    assertThat(warning.label).isEqualTo("01.06.2026")
    assertThat(warning.message).isEqualTo("Fehlende Angabe: gewicht")
  }

  @Test
  fun `rules run on aggregated and transformed results, labelled by bucket`() {
    val view = view(
      """
      queries:
        monate: { entity: Fahrt, timeField: datum, aggregate: { bucket: MONAT, values: { km: { sum: distanz } } } }
      transforms:
        monate: [{ calc: kmZahl, expr: "km / 1000" }]
      checks:
        viel: { rule: monate, field: kmZahl, max: 30, message: "{km} im {bucket}" }
      tabs:
        - name: P
          rows: [[ { warnings: Hinweise } ]]
      """,
    )

    val warning = warnings(view).warnings.single()
    assertThat(warning.label).isEqualTo("06/2026")
    assertThat(warning.message).endsWith("im 06/2026")
  }

  @Test
  fun `check definitions are validated`() {
    assertThat(
      errors(
        """
        queries:
          fahrten: { entity: Fahrt, timeField: datum }
          ohneZeit: { entity: Fahrt }
          summe: { entity: Fahrt, timeField: datum, aggregate: { bucket: MONAT, sum: distanz } }
        checks:
          leer: { message: x }
          doppelt: { gap: fahrten, rule: fahrten }
          zeit: { gap: ohneZeit, every: MONAT }
          aggregiert: { gap: summe, every: MONAT }
          periode: { gap: fahrten, every: MONTH }
          kalender: { gap: fahrten, every: TAG, exclude: [config.name, feiertage] }
          platzhalter: { gap: fahrten, every: TAG, message: "{woche}" }
          wert: { rule: fahrten, field: datum, max: 3 }
          grenzen: { rule: fahrten, field: distanz, min: viel, max: 1km }
          ohneGrenze: { rule: fahrten, field: distanz }
          spalte: { rule: fahrten, required: [unbekannt] }
          umgekehrt: { rule: fahrten, field: distanz, min: 10, max: 5 }
        tabs:
          - name: P
            rows: [[ { warnings: Hinweise, checks: [fehlt] } ]]
        """,
      ),
    ).containsExactly(
      ReportDefinitionErrorCode.CHECK_TYPE_MISSING to "checks.leer",
      ReportDefinitionErrorCode.CHECK_TYPE_AMBIGUOUS to "checks.doppelt",
      ReportDefinitionErrorCode.TIME_FIELD_MISSING to "checks.zeit.gap",
      ReportDefinitionErrorCode.KEY_CONFLICT to "checks.aggregiert.gap",
      ReportDefinitionErrorCode.INVALID_VALUE to "checks.periode.every",
      ReportDefinitionErrorCode.CALENDAR_INVALID to "checks.kalender.exclude[0]",
      ReportDefinitionErrorCode.INVALID_VALUE to "checks.kalender.exclude[1]",
      ReportDefinitionErrorCode.MESSAGE_PLACEHOLDER_UNKNOWN to "checks.platzhalter.message",
      ReportDefinitionErrorCode.FIELD_NOT_NUMERIC to "checks.wert.field",
      ReportDefinitionErrorCode.INVALID_NUMBER to "checks.grenzen.min",
      ReportDefinitionErrorCode.REQUIRED_KEY_MISSING to "checks.ohneGrenze",
      ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY to "checks.spalte.required[0]",
      ReportDefinitionErrorCode.INVALID_RANGE to "checks.umgekehrt.min",
      ReportDefinitionErrorCode.CHECK_NOT_FOUND to "tabs[0].rows[0][0].checks[0]",
    )
  }

  @Test
  fun `rule expressions only read numeric result fields and configuration values`() {
    assertThat(
      errors(
        """
        queries:
          fahrten: { entity: Fahrt, timeField: datum }
        checks:
          a: { rule: fahrten, expr: "distanz + config.name", max: 1 }
          b: { rule: fahrten, expr: "foo(distanz)", max: 1 }
          c: { rule: fahrten, expr: "unbekannt * 2", max: 1 }
        tabs: []
        """,
      ),
    ).containsExactly(
      ReportDefinitionErrorCode.FIELD_NOT_NUMERIC to "checks.a.expr",
      ReportDefinitionErrorCode.EXPRESSION_UNKNOWN_FUNCTION to "checks.b.expr",
      ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY to "checks.c.expr",
    )
  }
}
