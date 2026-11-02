package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.ChartPanelSpec
import de.chrgroth.james.platform.domain.model.app.ChartXAxisType
import de.chrgroth.james.platform.domain.model.app.ChartYAxisSpec
import de.chrgroth.james.platform.domain.model.app.PanelSpec
import de.chrgroth.james.platform.domain.model.app.RelativePeriod
import de.chrgroth.james.platform.domain.model.app.ReportDateRange
import de.chrgroth.james.platform.domain.model.app.ReportPanelType
import de.chrgroth.james.platform.domain.model.app.StatPanelSpec
import de.chrgroth.james.platform.domain.model.app.TablePanelSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Aggregate queries, time series, bar and stat panels (Reports 2/8), executed against the "Fahrten" fixtures with today = 15.06.2026. */
class ReportAggregationTests {

  private val today = LocalDate.of(2026, 6, 15)

  private val data = listOf(
    ReportFixtures.radData("r-1", "Gravel"),
    ReportFixtures.radData("r-2", "Rennrad"),
    ReportFixtures.fahrtData("f-1", "2026-03-05", 20_000, "r-1", gewicht = 80.0, zufriedenheit = 4),
    ReportFixtures.fahrtData("f-2", "2026-03-20", 12_500, "r-2", gewicht = 79.5, zufriedenheit = 2),
    ReportFixtures.fahrtData("f-3", "2026-05-02", 30_000, "r-1", gewicht = 78.0, elektrisch = true, zufriedenheit = 5),
    ReportFixtures.fahrtData("f-4", "2026-06-01", 10_000, "r-2", gewicht = 78.5),
    ReportFixtures.fahrtData("f-5", "2025-04-15", 5_000, "r-1"),
    ReportFixtures.fahrtData("f-6", "2025-04-05", 7_000, null),
  )

  private fun panels(yaml: String): List<PanelSpec> {
    val result = ReportDefinitionParser.parse(yaml.trimIndent(), ReportFixtures.entities)
    assertThat(result.errors).isEmpty()
    val executor = ReportExecutor(ReportFixtures.entities, data, today) { _, _ -> emptyMap() }
    return executor.execute("r", "Fahrten", result.definition!!).tabs.single().rows.single().panels
  }

  private fun TablePanelSpec.texts() = rows.map { row -> row.map { it.text } }

  @Test
  fun `sums per month bucket, fills gaps in the time series and plots in the display unit`() {
    val (series, table) = panels(
      """
      queries:
        km: { entity: Fahrt, timeField: datum, range: this_year, aggregate: { bucket: MONAT, sum: distanz } }
      tabs:
        - name: T
          rows: [[{ timeseries: Kilometer, query: km, style: column }, { table: Monate, query: km }]]
      """,
    )

    series as ChartPanelSpec
    assertThat(series.type).isEqualTo(ReportPanelType.TIMESERIES)
    assertThat(series.xAxis.type).isEqualTo(ChartXAxisType.CATEGORY)
    assertThat(series.xAxis.categories).containsExactly("03/2026", "04/2026", "05/2026", "06/2026")
    assertThat(series.yAxes).containsExactly(ChartYAxisSpec("left", "km"))
    assertThat(series.zoom).isTrue()
    val points = series.series.single().points
    assertThat(series.series.single().name).isEqualTo("sum")
    assertThat(series.series.single().style).isEqualTo("column")
    assertThat(points.map { it.y }).containsExactly(32.5, null, 30.0, 10.0)
    assertThat(points.map { it.text }).containsExactly("32km 500m", "", "30km", "10km")

    table as TablePanelSpec
    assertThat(table.columns.map { it.key }).containsExactly("bucket", "sum")
    assertThat(table.texts()).containsExactly(listOf("03/2026", "32km 500m"), listOf("05/2026", "30km"), listOf("06/2026", "10km"))
  }

  @Test
  fun `groups by a ref labelled by display text, sorts aggregated rows and charts them as bars`() {
    val (bar, table) = panels(
      """
      queries:
        proRad:
          entity: Fahrt
          aggregate: { groupBy: rad, values: { fahrten: count, km: { sum: distanz } } }
          sort: [{ field: fahrten, direction: DESC }]
      tabs:
        - name: T
          rows: [[{ bar: Fahrten pro Rad, query: proRad, field: fahrten, horizontal: true }, { table: Räder, query: proRad }]]
      """,
    )

    bar as ChartPanelSpec
    assertThat(bar.type).isEqualTo(ReportPanelType.BAR)
    assertThat(bar.horizontal).isTrue()
    assertThat(bar.zoom).isFalse()
    assertThat(bar.xAxis.categories).containsExactly("Gravel", "Rennrad", "–")
    assertThat(bar.series.single().points.map { it.y }).containsExactly(3.0, 2.0, 1.0)
    assertThat(bar.yAxes).containsExactly(ChartYAxisSpec("left", null))

    table as TablePanelSpec
    assertThat(table.texts()).containsExactly(listOf("Gravel", "3", "55km"), listOf("Rennrad", "2", "22km 500m"), listOf("", "1", "7km"))
  }

  @Test
  fun `combines bucket and group, honours the period start and splits time series per group`() {
    val (series, table) = panels(
      """
      queries:
        saison: { entity: Fahrt, timeField: datum, aggregate: { bucket: JAHR, periodStart: "10.04.", groupBy: rad, count: "*" } }
      tabs:
        - name: T
          rows: [[{ timeseries: Fahrten je Saison, query: saison }, { table: Saisons, query: saison }]]
      """,
    )

    table as TablePanelSpec
    assertThat(table.texts()).containsExactly(
      listOf("2024", "", "1"),
      listOf("2025", "Gravel", "2"),
      listOf("2025", "Rennrad", "1"),
      listOf("2026", "Gravel", "1"),
      listOf("2026", "Rennrad", "1"),
    )

    series as ChartPanelSpec
    assertThat(series.xAxis.categories).containsExactly("2024", "2025", "2026")
    assertThat(series.series.map { it.name }).containsExactly("–", "Gravel", "Rennrad")
    assertThat(series.series[1].points.map { it.y }).containsExactly(null, 2.0, 1.0)
  }

  @Test
  fun `aggregates nested object fields, the last value by time field and averages with units`() {
    val table = panels(
      """
      queries:
        werte:
          entity: Fahrt
          timeField: datum
          aggregate:
            values:
              zufriedenheit: { avg: bewertung.zufriedenheit }
              gewicht: { last: gewicht }
              schnitt: { avg: distanz }
              minimum: { min: distanz }
      tabs:
        - name: T
          rows: [[{ table: Werte, query: werte }]]
      """,
    ).single() as TablePanelSpec

    assertThat(table.columns.map { it.type }).containsExactly("DOUBLE", "DOUBLE", "DOUBLE", "LONG")
    assertThat(table.columns[1].unit?.symbol).isEqualTo("kg")
    assertThat(table.texts()).containsExactly(listOf("3,67", "78,5kg", "14km 83m", "5km"))
  }

  @Test
  fun `stat reduces a field and compares it to the previous period with threshold colors`() {
    val stat = panels(
      """
      queries:
        monat: { entity: Fahrt, timeField: datum, range: this_month, fields: [datum, distanz] }
      tabs:
        - name: T
          rows:
            - - stat: Kilometer
                query: monat
                field: distanz
                reduce: sum
                compare: previous_period
                thresholds: [{ value: 0, color: red }, { value: 20km, color: green }]
      """,
    ).single() as StatPanelSpec

    assertThat(stat.value).isEqualTo(10_000.0)
    assertThat(stat.text).isEqualTo("10km")
    assertThat(stat.color).isEqualTo("red")
    val comparison = stat.comparison!!
    assertThat(comparison.previousValue).isEqualTo(30_000.0)
    assertThat(comparison.previousText).isEqualTo("30km")
    assertThat(comparison.deltaText).isEqualTo("−20km")
    assertThat(comparison.percentText).isEqualTo("−66,67 %")
    assertThat(comparison.trend).isEqualTo("down")
    assertThat(comparison.periodText).isEqualTo("01.05.2026 – 31.05.2026")
  }

  @Test
  fun `stat counts rows, defaults to the single aggregate value and takes the last value`() {
    val panels = panels(
      """
      queries:
        alle: { entity: Fahrt, timeField: datum }
        summe: { entity: Fahrt, aggregate: { sum: distanz } }
      tabs:
        - name: T
          rows:
            - - { stat: Fahrten, query: alle, reduce: count }
              - { stat: Gesamt, query: summe, thresholds: [{ value: 50km, color: "#00aa00" }] }
              - { stat: Gewicht, query: alle, field: gewicht }
      """,
    ).map { it as StatPanelSpec }

    assertThat(panels.map { it.text }).containsExactly("6", "84km 500m", "78,5kg")
    assertThat(panels[1].color).isEqualTo("#00aa00")
    assertThat(panels.map { it.comparison }).containsOnlyNulls()
  }

  @Test
  fun `plain queries chart over a time axis and several series share or split axes`() {
    val (gewicht, kombiniert) = panels(
      """
      queries:
        gewicht: { entity: Fahrt, timeField: datum, range: this_year, fields: [datum, gewicht] }
        monate: { entity: Fahrt, timeField: datum, range: this_year, aggregate: { bucket: MONAT, values: { km: { sum: distanz }, fahrten: count } } }
      tabs:
        - name: T
          rows:
            - - { timeseries: Gewicht, query: gewicht, field: gewicht, zoom: false }
              - timeseries: Kombiniert
                query: monate
                series:
                  - { field: km, name: Kilometer, style: column }
                  - { field: fahrten, name: Fahrten, style: line, axis: right }
      """,
    ).map { it as ChartPanelSpec }

    assertThat(gewicht.xAxis.type).isEqualTo(ChartXAxisType.TIME)
    assertThat(gewicht.zoom).isFalse()
    assertThat(gewicht.series.single().points.map { it.x }).containsExactly("2026-03-05", "2026-03-20", "2026-05-02", "2026-06-01")
    assertThat(gewicht.series.single().points.map { it.text }).containsExactly("80kg", "79,5kg", "78kg", "78,5kg")
    assertThat(gewicht.yAxes).containsExactly(ChartYAxisSpec("left", "kg"))

    assertThat(kombiniert.yAxes).containsExactly(ChartYAxisSpec("left", "km"), ChartYAxisSpec("right", null))
    assertThat(kombiniert.series.map { Triple(it.name, it.style, it.yAxis) }).containsExactly(Triple("Kilometer", "column", 0), Triple("Fahrten", "line", 1))
    assertThat(kombiniert.series[1].points.map { it.y }).containsExactly(2.0, null, 1.0, 1.0)
  }

  @Test
  fun `booleans are shown as ja and nein, nested fields can be filtered`() {
    val table = panels(
      """
      queries:
        q:
          entity: Fahrt
          fields: [datum, elektrisch, bewertung.zufriedenheit]
          filter: [{ field: bewertung.zufriedenheit, op: GREATER_THAN, value: 3 }]
          sort: [datum]
      tabs:
        - name: T
          rows: [[{ table: Fahrten, query: q }]]
      """,
    ).single() as TablePanelSpec

    assertThat(table.texts()).containsExactly(listOf("05.03.2026", "nein", "4"), listOf("02.05.2026", "ja", "5"))
  }

  @Test
  fun `previous periods keep calendar months and otherwise shift by days`() {
    fun previous(period: RelativePeriod) = period.resolve(today).previous()

    assertThat(previous(RelativePeriod.THIS_MONTH)).isEqualTo(ReportDateRange(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
    assertThat(previous(RelativePeriod.THIS_YEAR)).isEqualTo(ReportDateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
    assertThat(previous(RelativePeriod.LAST_7_DAYS)).isEqualTo(ReportDateRange(LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 8)))
    assertThat(ReportDateRange(null, today).previous()).isNull()
  }
}
