package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.ChartPanelSpec
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.PanelSpec
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.ReportDefinitionErrorCode
import de.chrgroth.james.platform.domain.model.app.StatPanelSpec
import de.chrgroth.james.platform.domain.model.app.TablePanelSpec
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.model.app.encodeListValue
import de.chrgroth.james.platform.domain.model.app.encodeObjectValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/** The expression language in reports: typed `calc` fields (texts, yes/no, dates, times) and the `aggregate` step, ported from Kotlin steps. */
class ReportExpressionLanguageTests {

  private val today = LocalDate.of(2026, 11, 3)

  private val einstellungen = EntityDefinition(
    id = EntityDefinitionId("e-einstellungen"),
    name = "Einstellungen",
    configuration = true,
    properties = listOf(
      Property(PropertyId("p-eintritt"), "eintritt", PropertyType.DATE),
      Property(PropertyId("p-wochenstunden"), "wochenstunden", PropertyType.DOUBLE),
      Property(
        PropertyId("p-sollzeiten"),
        "sollzeiten",
        PropertyType.LIST,
        listItemType = PropertyType.OBJECT,
        nestedProperties = listOf(Property(PropertyId("p-s-ab"), "ab", PropertyType.DATE), Property(PropertyId("p-s-stunden"), "stunden", PropertyType.DOUBLE)),
      ),
    ),
  )

  private val arbeitstag = EntityDefinition(
    id = EntityDefinitionId("e-arbeitstag"),
    name = "Arbeitstag",
    properties = listOf(
      Property(PropertyId("p-a-datum"), "datum", PropertyType.DATE),
      Property(PropertyId("p-a-beginn"), "beginn", PropertyType.TIME),
      Property(PropertyId("p-a-ende"), "ende", PropertyType.TIME),
      Property(PropertyId("p-a-pause"), "pause", PropertyType.LONG),
      Property(PropertyId("p-a-art"), "art", PropertyType.STRING),
    ),
  )

  private val vertrag = EntityDefinition(
    id = EntityDefinitionId("e-vertrag"),
    name = "Vertrag",
    properties = listOf(
      Property(PropertyId("p-v-ab"), "gueltigAb", PropertyType.DATE),
      Property(PropertyId("p-v-stunden"), "wochenstunden", PropertyType.DOUBLE),
    ),
  )

  private val journal = EntityDefinition(
    id = EntityDefinitionId("e-journal"),
    name = "Journal",
    properties = listOf(
      Property(PropertyId("p-j-datum"), "datum", PropertyType.DATE),
      Property(PropertyId("p-j-kopf"), "kopfschmerz", PropertyType.BOOLEAN),
      Property(PropertyId("p-j-migraene"), "migraene", PropertyType.BOOLEAN),
      Property(PropertyId("p-j-befinden"), "befinden", PropertyType.STRING),
    ),
  )

  private val entities = ReportFixtures.entities + listOf(einstellungen, arbeitstag, vertrag, journal)

  private var created = Instant.parse("2026-01-01T00:00:00Z")

  private fun data(id: String, entity: EntityDefinition, values: Map<String, String?>): AppData {
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
      data = values.filterValues { it != null },
    )
  }

  private val sollzeiten = encodeListValue(
    listOf(
      encodeObjectValue(mapOf("p-s-ab" to "2026-03-01", "p-s-stunden" to "6")),
      encodeObjectValue(mapOf("p-s-ab" to "2026-01-01", "p-s-stunden" to "8")),
    ),
  )

  private val config = data("cfg", einstellungen, mapOf("p-eintritt" to "2026-01-01", "p-wochenstunden" to "40", "p-sollzeiten" to sollzeiten))

  private fun tag(id: String, datum: String, beginn: String?, ende: String?, pause: Long?, art: String) =
    data(id, arbeitstag, mapOf("p-a-datum" to datum, "p-a-beginn" to beginn, "p-a-ende" to ende, "p-a-pause" to pause?.toString(), "p-a-art" to art))

  private fun eintrag(id: String, datum: String, kopfschmerz: Boolean?, migraene: Boolean?, befinden: String?) = data(
    id,
    journal,
    mapOf("p-j-datum" to datum, "p-j-kopf" to kopfschmerz?.toString(), "p-j-migraene" to migraene?.toString(), "p-j-befinden" to befinden),
  )

  private val all = listOf(
    config,
    ReportFixtures.radData("r-1", "Gravel"),
    ReportFixtures.radData("r-2", "Rennrad"),
    ReportFixtures.fahrtData("f-1", "2026-01-10", 10_400, "r-1", elektrisch = true),
    ReportFixtures.fahrtData("f-2", "2026-03-05", 8_600, "r-2"),
    data("v-1", vertrag, mapOf("p-v-ab" to "2026-03-01", "p-v-stunden" to "30")),
    tag("t-0", "2025-12-31", "08:00", "12:00", 0, "A"),
    tag("t-1", "2026-02-02", "08:00", "16:30", 30, "A"),
    tag("t-2", "2026-03-02", "08:00", "15:00", 30, "A"),
    tag("t-3", "2026-03-03", null, null, null, "U"),
    tag("t-4", "2026-03-07", "10:00", "12:00", null, "A"),
    eintrag("j-1", "2026-10-01", true, false, "gut"),
    eintrag("j-2", "2026-10-02", false, false, "krank"),
    eintrag("j-3", "2026-10-03", false, true, "super"),
    eintrag("j-4", "2026-11-01", null, null, null),
  )

  private fun panels(yaml: String): List<PanelSpec> {
    val result = ReportDefinitionParser.parse(yaml.trimIndent(), entities)
    assertThat(result.errors).isEmpty()
    val executor = ReportExecutor(entities, all, today, config) { _, _ -> emptyMap() }
    return executor.execute("r", "Ausdrücke", result.definition!!).tabs.single().rows.flatMap { it.panels }
  }

  private fun errors(yaml: String) = ReportDefinitionParser.parse(yaml.trimIndent(), entities).errors.map { it.code to it.path }

  private fun TablePanelSpec.column(key: String): List<String?> = columns.indexOfFirst { it.key == key }.let { index -> rows.map { it[index].value } }

  private fun TablePanelSpec.texts(key: String): List<String> = columns.indexOfFirst { it.key == key }.let { index -> rows.map { it[index].text } }

  @Test
  fun `calc results are typed - texts, yes or no, dates, whole numbers and unit values - and references read as display text`() {
    val table = panels(
      """
      queries:
        fahrten: { entity: Fahrt, timeField: datum, fields: [datum, distanz, rad, elektrisch], sort: [datum] }
      transforms:
        fahrten:
          - { calc: lang, expr: "distanz > 9000" }
          - { calc: art, expr: "if(lang and elektrisch, 'E-Langstrecke', case(rad, 'Gravel', 'Gelände', 'Straße'))" }
          - { calc: km, expr: "distanz / 1000", type: LONG }
          - { calc: tage, expr: "days(datum, today())", unit: DAYS }
          - { calc: monat, expr: "month(datum)" }
          - { calc: start, expr: "datum", type: STRING }
          - { calc: frist, expr: "coalesce(date(datum), today())" }
          - { calc: wert, expr: "elektrisch + 1" }
      tabs:
        - name: T
          rows: [[{ table: Fahrten, query: fahrten }]]
      """,
    ).single() as TablePanelSpec

    assertThat(table.columns.associate { it.key to it.type }).containsAllEntriesOf(
      mapOf("lang" to "BOOLEAN", "art" to "STRING", "km" to "LONG", "tage" to "DOUBLE", "monat" to "DOUBLE", "start" to "STRING", "frist" to "DATE", "wert" to "DOUBLE"),
    )
    assertThat(table.column("lang")).containsExactly("true", "false")
    assertThat(table.texts("lang")).containsExactly("ja", "nein")
    assertThat(table.column("art")).containsExactly("E-Langstrecke", "Straße")
    assertThat(table.column("km")).containsExactly("10", "9")
    assertThat(table.column("tage")).containsExactly("297.0", "243.0")
    assertThat(table.columns.single { it.key == "tage" }.unit?.granularity).isEqualTo("DAYS")
    assertThat(table.column("monat")).containsExactly("1.0", "3.0")
    assertThat(table.column("start")).containsExactly("2026-01-10", "2026-03-05")
    assertThat(table.column("frist")).containsExactly("2026-01-10", "2026-03-05")
    assertThat(table.column("wert")).containsExactly("2.0", "1.0")
  }

  @Test
  fun `Arbeitszeiten - net minutes from times, target hours valid at the date with configuration fallback, type by condition, regrouped per year`() {
    val panels = panels(
      """
      queries:
        tage: { entity: Arbeitstag, timeField: datum, fields: [datum, beginn, ende, pause, art], sort: [datum] }
        vertraege: { entity: Vertrag, timeField: gueltigAb, fields: [gueltigAb, wochenstunden] }
      transforms:
        tage:
          - { asOf: vertraege, fields: [wochenstunden], prefix: vertrag_ }
          - { calc: typ, expr: "if(weekday(datum) >= 6, 'Wochenende', case(art, 'U', 'Urlaub', 'K', 'Krank', 'Arbeit'))" }
          - { calc: netto, expr: "if(typ == 'Arbeit', minutes(beginn, ende) - coalesce(pause, 0), 0)", unit: MINUTES }
          - calc: soll
            expr: "if(typ == 'Wochenende' or datum < config.eintritt, 0, coalesce(vertrag_wochenstunden, config.wochenstunden) * 60 / 5)"
            unit: MINUTES
          - { calc: saldo, expr: "if(typ == 'Arbeit', netto - soll, 0)", unit: MINUTES }
          - { aggregate: { bucket: JAHR, groupBy: typ, values: { tage: count, netto: { sum: netto }, saldo: { sum: saldo } } } }
      tabs:
        - name: T
          rows:
            - - { table: Jahre, query: tage }
              - { bar: Saldo, query: tage, field: saldo }
              - { timeseries: Netto, query: tage, field: netto }
      """,
    )
    val table = panels[0] as TablePanelSpec

    assertThat(table.columns.map { it.key }).containsExactly("bucket", "typ", "tage", "netto", "saldo")
    assertThat(table.column("bucket")).containsExactly("2025", "2026", "2026", "2026")
    assertThat(table.column("typ")).containsExactly("Arbeit", "Arbeit", "Urlaub", "Wochenende")
    assertThat(table.column("tage")).containsExactly("1", "2", "1", "1")
    assertThat(table.column("netto")).containsExactly("240.0", "870.0", "0.0", "0.0")
    assertThat(table.column("saldo")).containsExactly("240.0", "30.0", "0.0", "0.0")
    assertThat(table.columns.single { it.key == "netto" }.unit?.granularity).isEqualTo("MINUTES")

    val bar = panels[1] as ChartPanelSpec
    assertThat(bar.xAxis.categories).containsExactly("Arbeit", "Urlaub", "Wochenende")
    val timeseries = panels[2] as ChartPanelSpec
    assertThat(timeseries.xAxis.categories).containsExactly("2025", "2026")
  }

  @Test
  fun `valid_at picks the target hours of the configuration list item valid at the row's date`() {
    val table = panels(
      """
      queries:
        tage: { entity: Arbeitstag, timeField: datum, fields: [datum], sort: [datum] }
      transforms:
        tage:
          - { calc: soll, expr: "valid_at(config.sollzeiten, ab, datum, stunden) * 60", unit: MINUTES }
          - { calc: ab, expr: "valid_at(config.sollzeiten, ab, datum, ab)" }
      tabs:
        - name: T
          rows: [[{ table: Tage, query: tage }]]
      """,
    ).single() as TablePanelSpec

    assertThat(table.column("datum")).containsExactly("2025-12-31", "2026-02-02", "2026-03-02", "2026-03-03", "2026-03-07")
    assertThat(table.column("soll")).containsExactly(null, "480.0", "360.0", "360.0", "360.0")
    assertThat(table.columns.single { it.key == "ab" }.type).isEqualTo("DATE")
    assertThat(table.column("ab")).containsExactly(null, "2026-01-01", "2026-03-01", "2026-03-01", "2026-03-01")
  }

  @Test
  fun `list functions in reports read configuration lists only, with known item fields of comparable types`() {
    assertThat(
      errors(
        """
        queries:
          tage: { entity: Arbeitstag, timeField: datum, fields: [datum, art] }
        transforms:
          tage:
            - { calc: a, expr: "count_of(art)" }
            - { calc: b, expr: "valid_at(config.wochenstunden, ab, datum, stunden)" }
            - { calc: c, expr: "valid_at(config.sollzeiten, beginn, datum, stunden)" }
            - { calc: d, expr: "valid_at(config.sollzeiten, ab, art == 'A', stunden)" }
        tabs: []
        """,
      ),
    ).containsExactly(
      ReportDefinitionErrorCode.EXPRESSION_LIST_FIELD_REQUIRED to "transforms.tage[0].expr",
      ReportDefinitionErrorCode.EXPRESSION_LIST_FIELD_REQUIRED to "transforms.tage[1].expr",
      ReportDefinitionErrorCode.EXPRESSION_UNKNOWN_ITEM_FIELD to "transforms.tage[2].expr",
      ReportDefinitionErrorCode.EXPRESSION_INCOMPARABLE to "transforms.tage[3].expr",
    )
  }

  @Test
  fun `Journal - yes or no values as 1 or 0, a rating text mapped to a number, regrouped per month`() {
    val table = panels(
      """
      queries:
        journal: { entity: Journal, timeField: datum, fields: [datum, kopfschmerz, migraene, befinden], sort: [datum] }
      transforms:
        journal:
          - { calc: beschwerden, expr: "kopfschmerz or migraene", type: LONG }
          - { calc: kopfweh, expr: "max(kopfschmerz, migraene)" }
          - { calc: befindenWert, expr: "case(befinden, 'krank', 0, 'angeschlagen', 1, 'gut', 2, 'super', 3)", type: LONG }
          - { aggregate: { bucket: MONAT, values: { beschwerdeTage: { sum: beschwerden }, kopfweh: { max: kopfweh }, befinden: { avg: befindenWert } } } }
      tabs:
        - name: T
          rows: [[{ table: Monate, query: journal }]]
      """,
    ).single() as TablePanelSpec

    assertThat(table.column("bucket")).containsExactly("2026-10", "2026-11")
    assertThat(table.column("beschwerdeTage")).containsExactly("2", "0")
    assertThat(table.column("kopfweh")).containsExactly("1.0", null)
    assertThat(table.column("befinden")[0]!!.toDouble()).isCloseTo(5.0 / 3, within(0.001))
    assertThat(table.column("befinden")[1]).isNull()
  }

  @Test
  fun `the aggregate step without bucket totals per group, without group in one row`() {
    val panels = panels(
      """
      queries:
        fahrten: { entity: Fahrt, timeField: datum, fields: [datum, distanz, rad] }
        gesamt: { entity: Fahrt, timeField: datum, fields: [datum, distanz] }
      transforms:
        fahrten: [{ aggregate: { groupBy: rad, values: { km: { sum: distanz }, n: count } } }]
        gesamt: [{ aggregate: { sum: distanz } }]
      tabs:
        - name: T
          rows: [[{ table: Räder, query: fahrten }, { stat: Gesamt, query: gesamt }]]
      """,
    )
    val table = panels[0] as TablePanelSpec

    assertThat(table.columns.map { it.key }).containsExactly("rad", "km", "n")
    assertThat(table.texts("rad")).containsExactly("Gravel", "Rennrad")
    assertThat(table.column("km")).containsExactly("10400", "8600")
    assertThat((panels[1] as StatPanelSpec).value).isEqualTo(19_000.0)
  }

  @Test
  fun `invalid expressions and aggregate steps are reported with their path`() {
    assertThat(
      errors(
        """
        queries:
          fahrten: { entity: Fahrt, timeField: datum, fields: [datum, distanz, rad, elektrisch] }
          ohneZeit: { entity: Fahrt, fields: [distanz, rad] }
        transforms:
          fahrten:
            - { calc: a, expr: "rad * 2" }
            - { calc: b, expr: "minutes(datum, datum)" }
            - { calc: c, expr: "datum == 1" }
            - { calc: d, expr: "if(elektrisch, 1, 'nein')" }
            - { calc: e, expr: "datum", type: LONG }
            - { calc: f, expr: "today()", unit: DAYS }
            - { calc: g, expr: "1", type: STRING, unit: DAYS }
            - { calc: h, expr: "1", type: TEXT }
            - { calc: i, expr: "config.wochenstunden + config.fehlt" }
            - { aggregate: { values: { s: { sum: rad } } } }
          ohneZeit:
            - { aggregate: { bucket: JAHR, groupBy: fehlt, values: { n: count } } }
            - { aggregate: { foo: 1 } }
        tabs: []
        """,
      ),
    ).containsExactly(
      ReportDefinitionErrorCode.EXPRESSION_OPERAND_TYPE to "transforms.fahrten[0].expr",
      ReportDefinitionErrorCode.EXPRESSION_ARGUMENT_TYPE to "transforms.fahrten[1].expr",
      ReportDefinitionErrorCode.EXPRESSION_ARGUMENT_TYPE to "transforms.fahrten[1].expr",
      ReportDefinitionErrorCode.EXPRESSION_INCOMPARABLE to "transforms.fahrten[2].expr",
      ReportDefinitionErrorCode.EXPRESSION_MIXED_TYPES to "transforms.fahrten[3].expr",
      ReportDefinitionErrorCode.EXPRESSION_RESULT_TYPE to "transforms.fahrten[4].expr",
      ReportDefinitionErrorCode.EXPRESSION_RESULT_TYPE to "transforms.fahrten[5].expr",
      ReportDefinitionErrorCode.KEY_CONFLICT to "transforms.fahrten[6].unit",
      ReportDefinitionErrorCode.INVALID_VALUE to "transforms.fahrten[7].type",
      ReportDefinitionErrorCode.CONFIG_FIELD_NOT_FOUND to "transforms.fahrten[8].expr",
      ReportDefinitionErrorCode.FIELD_NOT_NUMERIC to "transforms.fahrten[9].aggregate.values.s",
      ReportDefinitionErrorCode.TRANSFORM_NEEDS_TIME to "transforms.ohneZeit[0].aggregate.bucket",
      ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY to "transforms.ohneZeit[0].aggregate.groupBy",
      ReportDefinitionErrorCode.UNKNOWN_KEY to "transforms.ohneZeit[1].aggregate.foo",
      ReportDefinitionErrorCode.REQUIRED_KEY_MISSING to "transforms.ohneZeit[1].aggregate",
    )
  }

  @Test
  fun `panels see the regrouped result - fields and x axis - and a regrouped query without bucket has no time any more`() {
    assertThat(
      errors(
        """
        queries:
          tage: { entity: Arbeitstag, timeField: datum, fields: [datum, art, pause] }
        transforms:
          tage: [{ aggregate: { groupBy: art, values: { pause: { sum: pause } } } }]
        tabs:
          - name: T
            rows:
              - - { table: T, query: tage, columns: [datum] }
                - { timeseries: Zeit, query: tage, field: pause }
                - { bar: Arten, query: tage }
        """,
      ),
    ).containsExactly(
      ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY to "tabs[0].rows[0][0].columns[0]",
      ReportDefinitionErrorCode.CHART_X_MISSING to "tabs[0].rows[0][1].query",
    )
  }

  @Test
  fun `the arc42 example of Arbeitszeiten and Journal is valid and renders`() {
    val panels = panels(
      """
      queries:
        tage: { entity: Arbeitstag, timeField: datum, fields: [datum, beginn, ende, pause, art], sort: [datum] }   # beginn/ende TIME, pause minutes
        vertraege: { entity: Vertrag, timeField: gueltigAb, fields: [gueltigAb, wochenstunden] }
        journal: { entity: Journal, timeField: datum, fields: [datum, kopfschmerz, migraene, befinden] }        # kopfschmerz/migraene BOOLEAN
      transforms:
        tage:
          - { asOf: vertraege, fields: [wochenstunden], prefix: vertrag_ }
          - { calc: typ, expr: "if(weekday(datum) >= 6, 'Wochenende', case(art, 'U', 'Urlaub', 'K', 'Krank', 'Arbeit'))" }
          - { calc: netto, expr: "if(typ == 'Arbeit', minutes(beginn, ende) - coalesce(pause, 0), 0)", unit: MINUTES }
          - calc: soll                                   # configuration: eintritt (DATE), wochenstunden (fallback without contract)
            expr: "if(typ == 'Wochenende' or datum < config.eintritt, 0, coalesce(vertrag_wochenstunden, config.wochenstunden) * 60 / 5)"
            unit: MINUTES
          - { calc: saldo, expr: "if(typ == 'Arbeit', netto - soll, 0)", unit: MINUTES }
          - { aggregate: { bucket: JAHR, groupBy: typ, values: { tage: count, netto: { sum: netto }, saldo: { sum: saldo } } } }
        journal:
          - { calc: beschwerden, expr: "kopfschmerz or migraene", type: LONG }      # yes/no -> 1/0
          - { calc: kopfweh, expr: "max(kopfschmerz, migraene)" }                     # the same via max
          - { calc: befindenWert, expr: "case(befinden, 'krank', 0, 'angeschlagen', 1, 'gut', 2, 'super', 3)", type: LONG }
          - { aggregate: { bucket: MONAT, values: { beschwerdeTage: { sum: beschwerden }, befinden: { avg: befindenWert } } } }
      tabs:
        - name: Übersicht
          rows:
            - - { table: Arbeitszeit pro Jahr, query: tage }
              - { bar: Saldo nach Art, query: tage, field: saldo }
              - { timeseries: Befinden, query: journal, series: [{ field: befinden }, { field: beschwerdeTage, style: column, axis: right }] }
      """,
    )

    assertThat((panels[0] as TablePanelSpec).column("saldo")).containsExactly("240.0", "30.0", "0.0", "0.0")
    assertThat((panels[2] as ChartPanelSpec).series.map { series -> series.points.map { it.y } }).containsExactly(listOf(5.0 / 3, null), listOf(2.0, 0.0))
  }
}
