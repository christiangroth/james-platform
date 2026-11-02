package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
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
import de.chrgroth.james.platform.domain.model.app.ReportStatPanel
import de.chrgroth.james.platform.domain.model.app.StatPanelSpec
import de.chrgroth.james.platform.domain.model.app.UnitFamily
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/** `config.<field>` references to the App configuration (ADR 0027) in filter values, stat thresholds and constant stat panels. */
class ReportConfigTests {

  private val today = LocalDate.of(2026, 6, 15)

  private val ziele = EntityDefinition(
    id = EntityDefinitionId("e-ziele"),
    name = "Ziele",
    configuration = true,
    properties = listOf(
      Property(
        PropertyId("p-z-ziel"),
        "zielKm",
        PropertyType.LONG,
        nullable = false,
        default = "50",
        unit = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.KILOMETERS, DistanceGranularity.KILOMETERS),
      ),
      Property(PropertyId("p-z-min"), "minDistanz", PropertyType.LONG, unit = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.METERS, DistanceGranularity.KILOMETERS)),
      Property(PropertyId("p-z-rad"), "lieblingsrad", PropertyType.STRING),
    ),
  )

  private val entities = ReportFixtures.entities + ziele

  private val data = listOf(
    ReportFixtures.radData("r-1", "Gravel"),
    ReportFixtures.radData("r-2", "Rennrad"),
    ReportFixtures.fahrtData("f-1", "2026-03-05", 20_000, "r-1"),
    ReportFixtures.fahrtData("f-2", "2026-03-20", 12_500, "r-2"),
    ReportFixtures.fahrtData("f-3", "2026-05-02", 30_000, "r-1"),
  )

  private fun configuration(values: Map<String, String?>) = AppData(
    id = AppDataId("cfg-1"),
    userId = "user",
    installedAppId = InstalledAppId("inst-1"),
    appVersion = VersionNumber("1.0.0"),
    lastValidatedWithVersion = VersionNumber("1.0.0"),
    entityType = ziele.id,
    objectVersion = 1,
    createdAt = Instant.EPOCH,
    lastChangedAt = Instant.EPOCH,
    data = values,
  )

  private val defaultConfig = configuration(mapOf("p-z-ziel" to "25", "p-z-min" to "15000", "p-z-rad" to "Gravel"))

  private fun panels(yaml: String, config: AppData? = defaultConfig): List<PanelSpec> {
    val result = ReportDefinitionParser.parse(yaml.trimIndent(), entities)
    assertThat(result.errors).isEmpty()
    val executor = ReportExecutor(entities, data + listOfNotNull(config), today, config) { _, _ -> emptyMap() }
    return executor.execute("r", "Ziele", result.definition!!).tabs.single().rows.single().panels
  }

  private fun errors(yaml: String, withEntities: List<EntityDefinition> = entities) =
    ReportDefinitionParser.parse(yaml.trimIndent(), withEntities).errors.map { it.code to it.path }

  @Test
  fun `filter values read the configuration, converted into the filtered field's unit`() {
    val (inMeters, inKilometers, byText) = panels(
      """
      queries:
        lang: { entity: Fahrt, filter: [{ field: distanz, op: GREATER_THAN, value: config.minDistanz }] }
        sehrLang: { entity: Fahrt, filter: [{ field: distanz, op: GREATER_THAN, value: config.zielKm }] }
        lieblingsrad: { entity: Fahrt, filter: [{ field: rad, op: EQUALS, value: config.lieblingsrad }] }
      tabs:
        - name: T
          rows: [[{ stat: Lang, query: lang, reduce: count }, { stat: Sehr lang, query: sehrLang, reduce: count }, { stat: Gravel, query: lieblingsrad, reduce: count }]]
      """,
    )

    assertThat((inMeters as StatPanelSpec).value).isEqualTo(2.0)
    assertThat((inKilometers as StatPanelSpec).value).isEqualTo(1.0)
    assertThat((byText as StatPanelSpec).value).isEqualTo(2.0)
  }

  @Test
  fun `a filter on a missing configuration value matches nothing`() {
    val (stat) = panels(
      """
      queries:
        lang: { entity: Fahrt, filter: [{ field: distanz, op: GREATER_THAN, value: config.minDistanz }] }
      tabs:
        - name: T
          rows: [[{ stat: Lang, query: lang, reduce: count }]]
      """,
      config = configuration(mapOf("p-z-ziel" to "25")),
    )

    assertThat((stat as StatPanelSpec).value).isEqualTo(0.0)
  }

  @Test
  fun `stat thresholds compare against configuration values in the stat's unit`() {
    val (reached, missed) = panels(
      """
      queries:
        km: { entity: Fahrt, timeField: datum, range: this_year, aggregate: { sum: distanz } }
        spaetMaerz: { entity: Fahrt, timeField: datum, range: { from: 2026-03-15, to: 2026-03-31 }, aggregate: { sum: distanz } }
      tabs:
        - name: T
          rows:
            - - { stat: Jahr, query: km, thresholds: [{ value: 0, color: red }, { value: config.zielKm, color: green }] }
              - { stat: Ende März, query: spaetMaerz, thresholds: [{ value: 0, color: red }, { value: config.zielKm, color: green }] }
      """,
    )

    assertThat((reached as StatPanelSpec).color).isEqualTo("green")
    assertThat((missed as StatPanelSpec).color).isEqualTo("red")
  }

  @Test
  fun `a stat panel shows a configuration field as constant`() {
    val (stat) = panels(
      """
      tabs:
        - name: T
          rows: [[{ stat: Jahresziel, config: zielKm, thresholds: [{ value: 10km, color: blue }] }]]
      """,
    )

    stat as StatPanelSpec
    assertThat(stat.value).isEqualTo(25.0)
    assertThat(stat.text).isEqualTo("25km")
    assertThat(stat.color).isEqualTo("blue")
  }

  @Test
  fun `a constant stat panel without configuration object has no value`() {
    val (stat) = panels(
      """
      tabs:
        - name: T
          rows: [[{ stat: Jahresziel, config: zielKm }]]
      """,
      config = null,
    )

    assertThat((stat as StatPanelSpec).text).isEqualTo("–")
  }

  @Test
  fun `parses a constant stat panel`() {
    val panel = ReportDefinitionParser.parse(
      """
      tabs:
        - name: T
          rows: [[{ stat: Jahresziel, config: config.zielKm }]]
      """.trimIndent(),
      entities,
    ).definition!!.tabs.single().rows.single().panels.single()

    assertThat(panel).isEqualTo(ReportStatPanel("Jahresziel", null, null, config = "zielKm"))
  }

  @Test
  fun `reports unknown configuration fields and a missing configuration entity`() {
    val yaml = """
      queries:
        lang: { entity: Fahrt, filter: [{ field: distanz, op: GREATER_THAN, value: config.unbekannt }] }
      tabs:
        - name: T
          rows: [[{ stat: Ziel, config: zielKm }]]
      """

    assertThat(errors(yaml)).containsExactly(ReportDefinitionErrorCode.CONFIG_FIELD_NOT_FOUND to "queries.lang.filter[0].value")
    assertThat(errors(yaml, ReportFixtures.entities)).containsExactly(
      ReportDefinitionErrorCode.CONFIG_NOT_DEFINED to "queries.lang.filter[0].value",
      ReportDefinitionErrorCode.CONFIG_NOT_DEFINED to "tabs[0].rows[0][0].config",
    )
  }

  @Test
  fun `reports configuration thresholds that are not numeric or of another unit, and query keys on a constant stat panel`() {
    assertThat(
      errors(
        """
        queries:
          gewicht: { entity: Fahrt, aggregate: { avg: gewicht } }
        tabs:
          - name: T
            rows:
              - - { stat: Gewicht, query: gewicht, thresholds: [{ value: config.zielKm, color: red }, { value: config.lieblingsrad, color: green }] }
                - { stat: Ziel, config: lieblingsrad, query: gewicht }
        """,
      ),
    ).containsExactlyInAnyOrder(
      ReportDefinitionErrorCode.CONFIG_UNIT_MISMATCH to "tabs[0].rows[0][0].thresholds[0].value",
      ReportDefinitionErrorCode.FIELD_NOT_NUMERIC to "tabs[0].rows[0][0].thresholds[1].value",
      ReportDefinitionErrorCode.KEY_CONFLICT to "tabs[0].rows[0][1].query",
      ReportDefinitionErrorCode.FIELD_NOT_NUMERIC to "tabs[0].rows[0][1].config",
    )
  }
}
