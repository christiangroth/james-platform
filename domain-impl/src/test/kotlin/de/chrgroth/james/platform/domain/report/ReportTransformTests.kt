package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.ChartPanelSpec
import de.chrgroth.james.platform.domain.model.app.ChartXAxisType
import de.chrgroth.james.platform.domain.model.app.DistanceGranularity
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.PanelSpec
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.ReportDefinitionErrorCode
import de.chrgroth.james.platform.domain.model.app.StatPanelSpec
import de.chrgroth.james.platform.domain.model.app.TablePanelSpec
import de.chrgroth.james.platform.domain.model.app.UnitFamily
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/** Transformation steps: calc, cumulative, moving average, year over year, forecast and resample. */
class ReportTransformTests {

  private val today = LocalDate.of(2026, 6, 15)

  private val person = EntityDefinition(
    id = EntityDefinitionId("e-person"),
    name = "Person",
    configuration = true,
    properties = listOf(
      Property(PropertyId("p-groesse"), "größe", PropertyType.LONG, unit = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.CENTIMETERS, DistanceGranularity.CENTIMETERS)),
      Property(PropertyId("p-name"), "name", PropertyType.STRING),
    ),
  )

  private val entities = ReportFixtures.entities + person

  private val config = AppData(
    id = AppDataId("cfg"),
    userId = "user",
    installedAppId = InstalledAppId("inst-1"),
    appVersion = VersionNumber("1.0.0"),
    lastValidatedWithVersion = VersionNumber("1.0.0"),
    entityType = person.id,
    objectVersion = 1,
    createdAt = Instant.EPOCH,
    lastChangedAt = Instant.EPOCH,
    data = mapOf("p-groesse" to "180", "p-name" to "Chris"),
  )

  private val data = listOf(
    ReportFixtures.radData("r-1", "Gravel"),
    ReportFixtures.radData("r-2", "Rennrad"),
    ReportFixtures.fahrtData("f-0", "2025-06-10", 8_000, "r-1", gewicht = 82.0),
    ReportFixtures.fahrtData("f-1", "2026-01-10", 10_000, "r-1", gewicht = 81.0),
    ReportFixtures.fahrtData("f-2", "2026-03-05", 20_000, "r-2", gewicht = 80.0),
    ReportFixtures.fahrtData("f-3", "2026-03-20", 5_000, "r-1", gewicht = 79.0),
    ReportFixtures.fahrtData("f-4", "2026-06-01", 30_000, "r-2", gewicht = null),
  )

  private fun panels(yaml: String): List<PanelSpec> {
    val result = ReportDefinitionParser.parse(yaml.trimIndent(), entities)
    assertThat(result.errors).isEmpty()
    val executor = ReportExecutor(entities, data + config, today, config) { _, _ -> emptyMap() }
    return executor.execute("r", "Transforms", result.definition!!).tabs.single().rows.flatMap { it.panels }
  }

  private fun errors(yaml: String) = ReportDefinitionParser.parse(yaml.trimIndent(), entities).errors.map { it.code to it.path }

  private fun TablePanelSpec.column(key: String): List<String?> = columns.indexOfFirst { it.key == key }.let { index -> rows.map { it[index].value } }

  @Test
  fun `calc computes a field per row from fields and configuration values`() {
    val table = panels(
      """
      queries:
        fahrten: { entity: Fahrt, timeField: datum, range: this_year, fields: [datum, gewicht], sort: [datum] }
      transforms:
        fahrten:
          - { calc: bmi, expr: "round(gewicht / (config.größe / 100) ^ 2, 1)" }
          - { calc: km, expr: "gewicht * 0 + 1000", unit: METERS }
      tabs:
        - name: T
          rows: [[{ table: BMI, query: fahrten }]]
      """,
    ).single() as TablePanelSpec

    assertThat(table.columns.map { it.key }).containsExactly("datum", "gewicht", "bmi", "km")
    assertThat(table.column("bmi")).containsExactly("25.0", "24.7", "24.4", null)
    assertThat(table.columns.last().unit?.granularity).isEqualTo("METERS")
    assertThat(table.rows.first().last().text).isEqualTo("1km")
  }

  @Test
  fun `cumulative and moving average run chronologically, whatever the display order`() {
    val table = panels(
      """
      queries:
        fahrten: { entity: Fahrt, timeField: datum, range: this_year, fields: [datum, distanz, gewicht], sort: [{ field: datum, direction: DESC }] }
      transforms:
        fahrten:
          - { cumulative: distanz, as: gesamt }
          - { movingAverage: gewicht, window: 2, as: gewicht_2 }
      tabs:
        - name: T
          rows: [[{ table: Fahrten, query: fahrten }]]
      """,
    ).single() as TablePanelSpec

    assertThat(table.column("datum")).containsExactly("2026-06-01", "2026-03-20", "2026-03-05", "2026-01-10")
    assertThat(table.column("gesamt")).containsExactly("65000", "35000", "30000", "10000")
    assertThat(table.column("gewicht_2")).containsExactly(null, "79.5", "80.5", "81.0")
  }

  @Test
  fun `cumulative in place keeps running per group of a grouped aggregate`() {
    val table = panels(
      """
      queries:
        km: { entity: Fahrt, timeField: datum, range: this_year, aggregate: { bucket: MONAT, groupBy: rad, values: { km: { sum: distanz } } } }
      transforms:
        km: [{ cumulative: km }]
      tabs:
        - name: T
          rows: [[{ table: Km, query: km }]]
      """,
    ).single() as TablePanelSpec

    assertThat(table.rows.map { row -> row.map { it.value } }).containsExactly(
      listOf("2026-01", "r-1", "10000"),
      listOf("2026-03", "r-1", "15000"),
      listOf("2026-03", "r-2", "20000"),
      listOf("2026-06", "r-2", "50000"),
    )
  }

  @Test
  fun `year over year reads the same bucket one year earlier`() {
    val table = panels(
      """
      queries:
        km: { entity: Fahrt, timeField: datum, range: this_year, aggregate: { bucket: MONAT, values: { km: { sum: distanz } } } }
      transforms:
        km: [{ yoy: km }]
      tabs:
        - name: T
          rows: [[{ table: Km, query: km }]]
      """,
    ).single() as TablePanelSpec

    assertThat(table.columns.map { it.key }).containsExactly("bucket", "km", "km_vorjahr")
    assertThat(table.column("bucket")).containsExactly("2026-01", "2026-03", "2026-06")
    assertThat(table.column("km_vorjahr")).containsExactly(null, null, "8000")
  }

  @Test
  fun `forecast extrapolates the current period and keeps completed ones`() {
    val (table, stat) = panels(
      """
      queries:
        monate: { entity: Fahrt, timeField: datum, range: this_year, aggregate: { bucket: MONAT, values: { km: { sum: distanz } } } }
        jahr: { entity: Fahrt, timeField: datum, range: this_year, aggregate: { sum: distanz } }
      transforms:
        monate: [{ forecast: km }]
        jahr: [{ forecast: sum, as: prognose }]
      tabs:
        - name: T
          rows: [[{ table: Monate, query: monate }, { stat: Prognose, query: jahr, field: prognose }]]
      """,
    )

    assertThat((table as TablePanelSpec).column("km_prognose")).containsExactly("10000.0", "25000.0", "60000.0")
    // 65 km after 166 of 365 days.
    assertThat((stat as StatPanelSpec).value).isCloseTo(65_000.0 * 365 / 166, within(0.01))
  }

  @Test
  fun `resample carries the latest value into every bucket, filling gaps forward`() {
    val (forward, none, chart) = panels(
      """
      queries:
        gewicht: { entity: Fahrt, timeField: datum, range: this_year, fields: [datum, gewicht] }
        luecken: { entity: Fahrt, timeField: datum, range: this_year, fields: [datum, gewicht] }
      transforms:
        gewicht: [{ resample: MONAT }]
        luecken: [{ resample: MONAT, fill: none }]
      tabs:
        - name: T
          rows:
            - [{ table: Vorwärts, query: gewicht }]
            - [{ table: Ohne, query: luecken }]
            - [{ timeseries: Gewicht, query: gewicht, field: gewicht }]
      """,
    )

    forward as TablePanelSpec
    assertThat(forward.columns.map { it.key }).containsExactly("bucket", "datum", "gewicht")
    assertThat(forward.column("bucket")).containsExactly("2026-01", "2026-02", "2026-03", "2026-04", "2026-05", "2026-06")
    assertThat(forward.column("gewicht")).containsExactly("81.0", "81.0", "79.0", "79.0", "79.0", null)
    assertThat((none as TablePanelSpec).column("gewicht")).containsExactly("81.0", null, "79.0", null, null, null)
    chart as ChartPanelSpec
    assertThat(chart.xAxis.type).isEqualTo(ChartXAxisType.CATEGORY)
    assertThat(chart.xAxis.categories).containsExactly("01/2026", "02/2026", "03/2026", "04/2026", "05/2026", "06/2026")
    assertThat(chart.series.single().points.map { it.y }).containsExactly(81.0, 81.0, 79.0, 79.0, 79.0, null)
  }

  @Test
  fun `transformed fields can be charted next to the source`() {
    val chart = panels(
      """
      queries:
        km: { entity: Fahrt, timeField: datum, range: this_year, aggregate: { bucket: MONAT, values: { km: { sum: distanz } } } }
      transforms:
        km: [{ cumulative: km, as: gesamt }]
      tabs:
        - name: T
          rows: [[{ timeseries: Km, query: km, series: [{ field: km, style: column }, { field: gesamt, axis: right }] }]]
      """,
    ).single() as ChartPanelSpec

    assertThat(chart.series.map { it.name }).containsExactly("km", "gesamt")
    assertThat(chart.series[1].points.map { it.y }).containsExactly(10.0, null, 35.0, null, null, 65.0)
  }

  @Test
  fun `invalid transforms are reported with their path`() {
    assertThat(
      errors(
        """
        queries:
          fahrten: { entity: Fahrt, timeField: datum, fields: [datum, distanz, elektrisch] }
          km: { entity: Fahrt, timeField: datum, aggregate: { groupBy: rad, values: { km: { sum: distanz } } } }
          ohneZeit: { entity: Fahrt, fields: [distanz] }
        transforms:
          fahrten:
            - { foo: bar }
            - { cumulative: distanz, yoy: distanz }
            - { calc: x, expr: "distanz +" }
            - { calc: y, expr: "exec(distanz)" }
            - { calc: z, expr: "unbekannt * datum" }
            - { calc: datum, expr: "1" }
            - { calc: w, expr: "1", unit: PARSEC }
            - { yoy: distanz }
            - { forecast: distanz }
            - { movingAverage: distanz }
          km: [{ resample: MONAT }]
          ohneZeit: [{ resample: TAG }]
          fehlt: []
        tabs: []
        """,
      ),
    ).containsExactly(
      ReportDefinitionErrorCode.TRANSFORM_STEP_MISSING to "transforms.fahrten[0]",
      ReportDefinitionErrorCode.TRANSFORM_STEP_AMBIGUOUS to "transforms.fahrten[1]",
      ReportDefinitionErrorCode.EXPRESSION_INVALID to "transforms.fahrten[2].expr",
      ReportDefinitionErrorCode.EXPRESSION_UNKNOWN_FUNCTION to "transforms.fahrten[3].expr",
      ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY to "transforms.fahrten[4].expr",
      ReportDefinitionErrorCode.EXPRESSION_OPERAND_TYPE to "transforms.fahrten[4].expr",
      ReportDefinitionErrorCode.DUPLICATE_RESULT_FIELD to "transforms.fahrten[5].calc",
      ReportDefinitionErrorCode.INVALID_VALUE to "transforms.fahrten[6].unit",
      ReportDefinitionErrorCode.TRANSFORM_NEEDS_BUCKET to "transforms.fahrten[7].yoy",
      ReportDefinitionErrorCode.FORECAST_NEEDS_PERIOD to "transforms.fahrten[8].forecast",
      ReportDefinitionErrorCode.REQUIRED_KEY_MISSING to "transforms.fahrten[9]",
      ReportDefinitionErrorCode.KEY_CONFLICT to "transforms.km[0].resample",
      ReportDefinitionErrorCode.RESAMPLE_NEEDS_TIME to "transforms.ohneZeit[0].resample",
      ReportDefinitionErrorCode.QUERY_NOT_FOUND to "transforms.fehlt",
    )
  }

  @Test
  fun `panels see transformed fields, and resampled queries only share an x axis with the same bucket`() {
    assertThat(
      errors(
        """
        queries:
          gewicht: { entity: Fahrt, timeField: datum, fields: [datum, gewicht] }
          km: { entity: Fahrt, timeField: datum, aggregate: { bucket: WOCHE, values: { km: { sum: distanz } } } }
        transforms:
          gewicht: [{ resample: MONAT }, { calc: doppelt, expr: "gewicht * 2" }]
        tabs:
          - name: T
            rows:
              - - { table: Gewicht, query: gewicht, columns: [bucket, doppelt, nichtDa] }
                - { timeseries: Mix, series: [{ query: gewicht, field: doppelt }, { query: km, field: km, axis: right }] }
        """,
      ),
    ).containsExactly(
      ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY to "tabs[0].rows[0][0].columns[2]",
      ReportDefinitionErrorCode.SERIES_X_MISMATCH to "tabs[0].rows[0][1].series[1].query",
    )
  }
}
