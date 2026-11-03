package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.ChartPanelSpec
import de.chrgroth.james.platform.domain.model.app.CurrencyGranularity
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
import de.chrgroth.james.platform.domain.model.app.TimeGranularity
import de.chrgroth.james.platform.domain.model.app.UnitFamily
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/** Advanced transformation steps: delta, as-of join, join, top-N and goals (Zählerstände, Urlaubsplaner, Verdienst, Running). */
class ReportAdvancedTransformTests {

  private val today = LocalDate.of(2026, 6, 15)

  private val meters = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.METERS, DistanceGranularity.KILOMETERS)
  private val cents = PropertyUnit(UnitFamily.CURRENCY, CurrencyGranularity.CENTS, CurrencyGranularity.EUROS)

  private val zaehlerstand = entity(
    "Zaehlerstand",
    Property(PropertyId("z-datum"), "datum", PropertyType.DATE),
    Property(PropertyId("z-stand"), "stand", PropertyType.LONG),
    Property(PropertyId("z-zaehler"), "zaehler", PropertyType.STRING),
  )

  private val vertrag = entity(
    "Vertrag",
    Property(PropertyId("v-ab"), "gueltigAb", PropertyType.DATE),
    Property(PropertyId("v-stunden"), "wochenstunden", PropertyType.DOUBLE),
    Property(PropertyId("v-urlaub"), "urlaubstage", PropertyType.LONG),
    Property(PropertyId("v-name"), "arbeitgeber", PropertyType.STRING),
  )

  private val urlaub = entity(
    "Urlaub",
    Property(PropertyId("u-datum"), "datum", PropertyType.DATE),
    Property(PropertyId("u-tage"), "tage", PropertyType.LONG),
  )

  private val arbeitstag = entity("Arbeitstag", Property(PropertyId("a-datum"), "datum", PropertyType.DATE))

  private val abrechnung = entity(
    "Abrechnung",
    Property(PropertyId("g-monat"), "monat", PropertyType.DATE),
    Property(PropertyId("g-netto"), "netto", PropertyType.LONG, unit = cents),
    Property(PropertyId("g-vertrag"), "vertrag", PropertyType.REF, targetEntityId = EntityDefinitionId("e-Vertrag")),
  )

  private val lauf = entity(
    "Lauf",
    Property(PropertyId("l-datum"), "datum", PropertyType.DATE),
    Property(PropertyId("l-distanz"), "distanz", PropertyType.LONG, unit = meters),
    Property(PropertyId("l-dauer"), "dauer", PropertyType.LONG, unit = PropertyUnit(UnitFamily.TIME, TimeGranularity.SECONDS, TimeGranularity.MINUTES)),
    Property(PropertyId("l-strecke"), "strecke", PropertyType.STRING),
  )

  private val entities = listOf(zaehlerstand, vertrag, urlaub, arbeitstag, abrechnung, lauf)

  private var created = Instant.parse("2026-01-01T00:00:00Z")

  private val data = listOf(
    data("z1", zaehlerstand, "z-datum" to "2026-01-01", "z-stand" to "1000", "z-zaehler" to "Strom"),
    data("z2", zaehlerstand, "z-datum" to "2026-01-11", "z-stand" to "1100", "z-zaehler" to "Strom"),
    data("z3", zaehlerstand, "z-datum" to "2026-02-10", "z-stand" to "1400", "z-zaehler" to "Strom"),
    data("z4", zaehlerstand, "z-datum" to "2026-01-01", "z-stand" to "50", "z-zaehler" to "Gas"),
    data("z5", zaehlerstand, "z-datum" to "2026-01-31", "z-stand" to "80", "z-zaehler" to "Gas"),
    data("v1", vertrag, "v-ab" to "2025-01-01", "v-stunden" to "40", "v-urlaub" to "30", "v-name" to "Alt GmbH"),
    data("v2", vertrag, "v-ab" to "2026-04-01", "v-stunden" to "32", "v-urlaub" to "28", "v-name" to "Neu AG"),
    data("u1", urlaub, "u-datum" to "2026-02-01", "u-tage" to "5"),
    data("u2", urlaub, "u-datum" to "2026-07-01", "u-tage" to "10"),
    data("u3", urlaub, "u-datum" to "2025-08-01", "u-tage" to "20"),
    data("a1", arbeitstag, "a-datum" to "2026-03-31"),
    data("a2", arbeitstag, "a-datum" to "2026-04-01"),
    data("a3", arbeitstag, "a-datum" to "2024-12-31"),
    data("g1", abrechnung, "g-monat" to "2026-03-01", "g-netto" to "300000", "g-vertrag" to "v1"),
    data("g2", abrechnung, "g-monat" to "2026-04-01", "g-netto" to "280000", "g-vertrag" to "v2"),
    data("g3", abrechnung, "g-monat" to "2026-05-01", "g-netto" to "280000", "g-vertrag" to null),
    data("l1", lauf, "l-datum" to "2025-05-01", "l-distanz" to "10000", "l-dauer" to "3000", "l-strecke" to "10k"),
    data("l2", lauf, "l-datum" to "2026-01-10", "l-distanz" to "10000", "l-dauer" to "2900", "l-strecke" to "10k"),
    data("l3", lauf, "l-datum" to "2026-02-10", "l-distanz" to "21100", "l-dauer" to "7000", "l-strecke" to "HM"),
    data("l4", lauf, "l-datum" to "2026-03-10", "l-distanz" to "5000", "l-dauer" to "1500", "l-strecke" to "5k"),
    data("l5", lauf, "l-datum" to "2026-03-20", "l-distanz" to "9500", "l-dauer" to "3100", "l-strecke" to "10k"),
  )

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

  private fun panels(yaml: String): List<PanelSpec> {
    val result = ReportDefinitionParser.parse(yaml.trimIndent(), entities)
    assertThat(result.errors).isEmpty()
    val executor = ReportExecutor(entities, data, today) { _, _ -> emptyMap() }
    return executor.execute("r", "Advanced", result.definition!!).tabs.single().rows.flatMap { it.panels }
  }

  private fun table(yaml: String) = panels(yaml).single() as TablePanelSpec

  private fun errors(yaml: String) = ReportDefinitionParser.parse(yaml.trimIndent(), entities).errors.map { it.code to it.path }

  private fun TablePanelSpec.column(key: String): List<String?> = columns.indexOfFirst { it.key == key }.also { assertThat(it).isNotNegative() }.let { index -> rows.map { it[index].value } }

  // region delta

  @Test
  fun `delta computes the difference to the previous reading per meter, optionally per day`() {
    val table = table(
      """
      queries:
        staende: { entity: Zaehlerstand, timeField: datum, sort: [zaehler, datum] }
      transforms:
        staende:
          - { delta: stand, as: verbrauch, by: zaehler }
          - { delta: stand, as: proTag, perDay: true, by: zaehler }
      tabs:
        - name: T
          rows: [[{ table: Stände, query: staende, columns: [zaehler, datum, verbrauch, proTag] }]]
      """,
    )

    assertThat(table.column("zaehler")).containsExactly("Gas", "Gas", "Strom", "Strom", "Strom")
    assertThat(table.column("verbrauch")).containsExactly(null, "30", null, "100", "300")
    assertThat(table.column("proTag")).containsExactly(null, "1.0", null, "10.0", "10.0")
  }

  @Test
  fun `delta with bucket spreads the consumption over the days between readings and sums it per bucket and meter`() {
    val panels = panels(
      """
      queries:
        staende: { entity: Zaehlerstand, timeField: datum }
      transforms:
        staende: [{ delta: stand, as: verbrauch, by: zaehler, bucket: MONAT }]
      tabs:
        - name: T
          rows: [[{ table: Verbrauch, query: staende }, { timeseries: Verbrauch, query: staende, field: verbrauch }]]
      """,
    )
    val table = panels[0] as TablePanelSpec
    val chart = panels[1] as ChartPanelSpec

    assertThat(table.columns.map { it.key }).containsExactly("bucket", "zaehler", "verbrauch")
    assertThat(table.rows.map { row -> row.map { it.value } }).containsExactly(
      listOf("2026-01", "Gas", "30.0"),
      listOf("2026-01", "Strom", "310.0"),
      listOf("2026-02", "Strom", "90.0"),
    )
    assertThat(chart.xAxis.categories).containsExactly("01/2026", "02/2026")
    assertThat(chart.series.map { it.name }).containsExactly("Gas", "Strom")
  }

  // endregion

  // region as-of and join

  @Test
  fun `asOf attaches the values valid at each row's date`() {
    val table = table(
      """
      queries:
        tage: { entity: Arbeitstag, timeField: datum, sort: [datum] }
        vertraege: { entity: Vertrag, timeField: gueltigAb }
      transforms:
        tage: [{ asOf: vertraege, fields: [wochenstunden, arbeitgeber], prefix: vertrag_ }]
      tabs:
        - name: T
          rows: [[{ table: Tage, query: tage }]]
      """,
    )

    assertThat(table.columns.map { it.key }).containsExactly("datum", "vertrag_wochenstunden", "vertrag_arbeitgeber")
    assertThat(table.column("vertrag_wochenstunden")).containsExactly(null, "40", "32")
    assertThat(table.column("vertrag_arbeitgeber")).containsExactly(null, "Alt GmbH", "Neu AG")
  }

  @Test
  fun `Urlaubsplaner - vacation days taken per year against the entitlement valid at the year's end, with a goal`() {
    val table = table(
      """
      queries:
        urlaub: { entity: Urlaub, timeField: datum, aggregate: { bucket: JAHR, values: { genommen: { sum: tage } } } }
        anspruch: { entity: Vertrag, timeField: gueltigAb, fields: [gueltigAb, urlaubstage] }
      transforms:
        urlaub:
          - { asOf: anspruch, fields: [urlaubstage], at: end }
          - { calc: rest, expr: "urlaubstage - genommen" }
          - { goal: genommen, target: urlaubstage, as: anspruch, progress: quote }
      tabs:
        - name: T
          rows: [[{ table: Urlaub, query: urlaub }]]
      """,
    )

    assertThat(table.column("bucket")).containsExactly("2025", "2026")
    assertThat(table.column("urlaubstage")).containsExactly("30", "28")
    assertThat(table.column("rest")).containsExactly("10.0", "13.0")
    assertThat(table.column("anspruch")).containsExactly("30.0", "28.0")
    assertThat(table.column("quote")[1]!!.toDouble()).isCloseTo(15.0 / 28 * 100, within(0.001))
  }

  @Test
  fun `asOf at the bucket start takes the value valid when the period begins`() {
    val table = table(
      """
      queries:
        urlaub: { entity: Urlaub, timeField: datum, aggregate: { bucket: JAHR, values: { genommen: { sum: tage } } } }
        anspruch: { entity: Vertrag, timeField: gueltigAb }
      transforms:
        urlaub: [{ asOf: anspruch, fields: [urlaubstage] }]
      tabs:
        - name: T
          rows: [[{ table: Urlaub, query: urlaub }]]
      """,
    )

    assertThat(table.column("urlaubstage")).containsExactly("30", "30")
  }

  @Test
  fun `Verdienst - join pay slips with their contract via the reference and yearly sums by bucket`() {
    val panels = panels(
      """
      queries:
        abrechnungen: { entity: Abrechnung, timeField: monat, fields: [monat, netto, vertrag], sort: [monat] }
        vertraege: { entity: Vertrag, fields: [arbeitgeber, wochenstunden] }
        netto_jahr: { entity: Abrechnung, timeField: monat, aggregate: { bucket: JAHR, values: { netto: { sum: netto } } } }
        urlaub_jahr: { entity: Urlaub, timeField: datum, aggregate: { bucket: JAHR, values: { tage: { sum: tage } } } }
      transforms:
        abrechnungen:
          - { join: vertraege, on: { field: vertrag, other: id }, fields: [arbeitgeber, wochenstunden] }
          - { calc: netto40h, expr: "netto * 40 / wochenstunden", unit: CENTS }
        netto_jahr: [{ join: urlaub_jahr, on: bucket, fields: [tage] }]
      tabs:
        - name: T
          rows: [[{ table: Abrechnungen, query: abrechnungen }, { table: Jahre, query: netto_jahr }]]
      """,
    )
    val slips = panels[0] as TablePanelSpec
    val years = panels[1] as TablePanelSpec

    assertThat(slips.column("arbeitgeber")).containsExactly("Alt GmbH", "Neu AG", null)
    assertThat(slips.column("netto40h")).containsExactly("300000.0", "350000.0", null)
    assertThat(years.rows.map { row -> row.map { it.value } }).containsExactly(listOf("2026", "860000", "15"))
  }

  // endregion

  // region top and goals

  @Test
  fun `Running - fastest run per route and the two longest runs per year`() {
    val panels = panels(
      """
      queries:
        laeufe: { entity: Lauf, timeField: datum, fields: [datum, strecke, distanz, dauer] }
        lang: { entity: Lauf, timeField: datum, fields: [datum, distanz] }
      transforms:
        laeufe:
          - { calc: pace, expr: "dauer / (distanz / 1000)", unit: SECONDS }
          - { top: dauer, order: asc, by: strecke }
        lang: [{ top: distanz, n: 2, bucket: JAHR, as: platz }]
      tabs:
        - name: T
          rows: [[{ table: Bestzeiten, query: laeufe }, { table: Längste, query: lang }]]
      """,
    )
    val records = panels[0] as TablePanelSpec
    val longest = panels[1] as TablePanelSpec

    assertThat(records.column("strecke")).containsExactly("10k", "5k", "HM")
    assertThat(records.column("dauer")).containsExactly("2900", "1500", "7000")
    assertThat(records.column("rang")).containsExactly("1", "1", "1")
    assertThat(records.column("pace")).containsExactly("290.0", "300.0", (7000 / 21.1).toString())
    assertThat(longest.columns.map { it.key }).containsExactly("bucket", "datum", "distanz", "platz")
    assertThat(longest.rows.map { row -> row.map { it.value } }).containsExactly(
      listOf("2025", "2025-05-01", "10000", "1"),
      listOf("2026", "2026-02-10", "21100", "1"),
      listOf("2026", "2026-01-10", "10000", "2"),
    )
    assertThat(longest.rows.first().first().text).isEqualTo("2025")
  }

  @Test
  fun `Running - a yearly goal pro rata per month against the cumulative distance, and as stat with progress`() {
    val panels = panels(
      """
      queries:
        km: { entity: Lauf, timeField: datum, range: this_year, aggregate: { bucket: MONAT, values: { km: { sum: distanz } } } }
      transforms:
        km:
          - { cumulative: km, as: gesamt }
          - { goal: gesamt, target: 1200km, period: JAHR, as: soll }
      tabs:
        - name: T
          rows:
            - - { table: Ziel, query: km }
              - { timeseries: Ziel, query: km, series: [{ field: gesamt }, { field: soll }] }
              - { stat: Jahresziel, query: km, field: km, reduce: sum, goal: 1200km }
      """,
    )
    val table = panels[0] as TablePanelSpec
    val stat = panels[2] as StatPanelSpec

    assertThat(table.column("bucket")).containsExactly("2026-01", "2026-02", "2026-03")
    assertThat(table.column("soll")[0]!!.toDouble()).isCloseTo(1_200_000.0 * 31 / 365, within(0.001))
    assertThat(table.column("soll")[2]!!.toDouble()).isCloseTo(1_200_000.0 * 90 / 365, within(0.001))
    assertThat(table.column("gesamt_fortschritt")[2]!!.toDouble()).isCloseTo(45_600.0 / (1_200_000.0 * 90 / 365) * 100, within(0.001))
    assertThat((panels[1] as ChartPanelSpec).series).hasSize(2)
    assertThat(stat.goal?.value).isEqualTo(1_200_000.0)
    assertThat(stat.goal?.percent!!).isCloseTo(45_600.0 / 1_200_000 * 100, within(0.001))
    assertThat(stat.goal?.percentText).isEqualTo("4 %")
  }

  // endregion

  // region validation

  @Test
  fun `a query read by asOf is validated with its own transforms, whatever the declaration order`() {
    assertThat(
      errors(
        """
        queries:
          tage: { entity: Arbeitstag, timeField: datum }
          vertraege: { entity: Vertrag, timeField: gueltigAb }
        transforms:
          tage: [{ asOf: vertraege, fields: [tagesstunden] }]
          vertraege: [{ calc: tagesstunden, expr: "wochenstunden / 5" }]
        tabs: []
        """,
      ),
    ).isEmpty()
  }

  @Test
  fun `cycles between asOf and join steps are rejected`() {
    assertThat(
      errors(
        """
        queries:
          a: { entity: Arbeitstag, timeField: datum }
          b: { entity: Vertrag, timeField: gueltigAb }
        transforms:
          a: [{ asOf: b, fields: [wochenstunden] }]
          b: [{ join: a, on: { field: gueltigAb, other: datum }, fields: [datum] }]
        tabs: []
        """,
      ),
    ).contains(ReportDefinitionErrorCode.TRANSFORM_QUERY_CYCLE to "transforms.b[0].join")
  }

  @Test
  fun `invalid advanced steps are reported with their path`() {
    assertThat(
      errors(
        """
        queries:
          vertraege: { entity: Vertrag, fields: [arbeitgeber, wochenstunden] }
          staende: { entity: Zaehlerstand, timeField: datum }
          laeufe: { entity: Lauf, timeField: datum }
        transforms:
          vertraege:
            - { delta: wochenstunden }
            - { top: arbeitgeber }
          staende:
            - { delta: stand, perDay: true, bucket: MONAT }
            - { asOf: vertraege, fields: [wochenstunden] }
            - { join: vertraege, fields: [gehalt] }
            - { join: vertraege, on: zaehler, fields: [arbeitgeber] }
          laeufe:
            - { goal: distanz, target: viel }
            - { goal: dauer, target: 10km, as: x }
            - { top: distanz, bucket: MONAT, as: datum }
        tabs:
          - name: T
            rows: [[{ stat: S, query: laeufe, field: distanz, reduce: sum, goal: config.ziel }]]
        """,
      ),
    ).contains(
      ReportDefinitionErrorCode.TRANSFORM_NEEDS_TIME to "transforms.vertraege[0].delta",
      ReportDefinitionErrorCode.FIELD_NOT_NUMERIC to "transforms.vertraege[1].top",
      ReportDefinitionErrorCode.KEY_CONFLICT to "transforms.staende[0].perDay",
      ReportDefinitionErrorCode.TRANSFORM_NEEDS_TIME to "transforms.staende[1].asOf",
      ReportDefinitionErrorCode.REQUIRED_KEY_MISSING to "transforms.staende[2]",
      ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY to "transforms.staende[2].fields[0]",
      ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY to "transforms.staende[3].on",
      ReportDefinitionErrorCode.GOAL_TARGET_INVALID to "transforms.laeufe[0].target",
      ReportDefinitionErrorCode.GOAL_TARGET_INVALID to "transforms.laeufe[1].target",
      ReportDefinitionErrorCode.DUPLICATE_RESULT_FIELD to "transforms.laeufe[2].as",
      ReportDefinitionErrorCode.CONFIG_NOT_DEFINED to "tabs[0].rows[0][0].goal",
    )
  }

  // endregion
}
