package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.error.InvalidReportDefinitionError
import de.chrgroth.james.platform.domain.error.ReportError
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.Report
import de.chrgroth.james.platform.domain.model.app.ReportId
import de.chrgroth.james.platform.domain.model.app.ReportPanelType
import de.chrgroth.james.platform.domain.model.app.ReportUnitSpec
import de.chrgroth.james.platform.domain.model.app.TablePanelSpec
import de.chrgroth.james.platform.domain.model.app.TextPanelSpec
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.port.`in`.app.ComputedPropertyPort
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import de.chrgroth.james.platform.domain.port.`in`.app.AppConfigurationPort
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.StatPanelSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ReportServiceTests {

  private val installedAppRepository: InstalledAppRepositoryPort = mockk()
  private val appVersionRepository: AppVersionRepositoryPort = mockk()
  private val appDataRepository: AppDataRepositoryPort = mockk()
  private val computedProperty: ComputedPropertyPort = mockk {
    every { computeValues(any(), any(), any(), any(), any()) } answers { mapOf("c-jahr" to secondArg<Map<String, String?>>()["p-datum"]?.take(4)) }
  }
  private val appConfiguration: AppConfigurationPort = mockk()
  private val service = ReportService(installedAppRepository, appVersionRepository, appDataRepository, computedProperty, appConfiguration).apply {
    clock = Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC)
  }

  private val installedApp = InstalledApp(InstalledAppId("inst-1"), "alice", AppId("app-1"), VersionNumber("1.0.0"), Instant.EPOCH)

  private val data = listOf(
    ReportFixtures.bandData("b-1", "Die Ärzte", "Punk"),
    ReportFixtures.bandData("b-2", "Muse"),
    ReportFixtures.konzertData("k-1", "2025-03-10", "b-1", "Hamburg", 4_550, besucher = 900),
    ReportFixtures.konzertData("k-2", "2025-11-02", "b-2", "Berlin", 12_000, besucher = 15_000),
    ReportFixtures.konzertData("k-3", "2026-05-01", "b-2", "Hamburg", null, besucher = 20_000),
    ReportFixtures.konzertData("k-4", "2024-01-20", "b-1", "Hamburg", 3_000),
  )

  private fun withReport(definition: String, entities: List<EntityDefinition> = ReportFixtures.entities) {
    val version = AppVersion(
      id = AppVersionId("ver-1"),
      appId = AppId("app-1"),
      versionNumber = VersionNumber("1.0.0"),
      releaseNotes = "x",
      entityDefinitions = entities,
      reports = listOf(Report(ReportId("r-1"), "Konzerte", definition.trimIndent())),
      status = AppVersionStatus.PUBLISHED,
      createdAt = Instant.EPOCH,
    )
    every { installedAppRepository.findById(InstalledAppId("inst-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns version
    every { appDataRepository.findAllByInstalledAppId(InstalledAppId("inst-1")) } returns data
  }

  private fun table(definition: String): TablePanelSpec {
    withReport(definition)
    val view = service.renderReport("alice", "inst-1", "r-1").getOrNull()!!
    return view.tabs.single().rows.single().panels.single() as TablePanelSpec
  }

  private fun TablePanelSpec.texts() = rows.map { row -> row.map { it.text } }

  @Test
  fun `uses the configuration defaults while the configuration object does not exist yet and passes them to computed properties`() {
    val ziele = EntityDefinition(
      id = EntityDefinitionId("e-ziele"),
      name = "Ziele",
      configuration = true,
      properties = listOf(Property(PropertyId("p-z-besucher"), "besucherZiel", PropertyType.LONG, nullable = false, default = "1000")),
    )
    withReport(
      """
      queries:
        k: { entity: Konzert, fields: [jahr] }
      tabs:
        - name: T
          rows: [[{ stat: Ziel, config: besucherZiel }, { table: Konzerte, query: k }]]
      """,
      ReportFixtures.entities + ziele,
    )
    every { appConfiguration.configurationValues(installedApp, any()) } returns mapOf("p-z-besucher" to "1000")

    val panels = service.renderReport("alice", "inst-1", "r-1").getOrNull()!!.tabs.single().rows.single().panels

    assertThat((panels[0] as StatPanelSpec).text).isEqualTo("1.000")
    verify { computedProperty.computeValues(any(), any(), any(), mapOf("p-z-besucher" to "1000"), any()) }
  }

  @Test
  fun `renders tabs, rows and text panels`() {
    withReport(
      """
      tabs:
        - name: Übersicht
          rows:
            - - { text: Hinweis, markdown: "**Hallo**" }
        - name: Leer
      """,
    )

    val view = service.renderReport("alice", "inst-1", "r-1").getOrNull()!!

    assertThat(view.name).isEqualTo("Konzerte")
    assertThat(view.tabs.map { it.name }).containsExactly("Übersicht", "Leer")
    assertThat(view.tabs[0].rows.single().panels.single()).isEqualTo(TextPanelSpec("Hinweis", "**Hallo**"))
  }

  @Test
  fun `table formats values like display texts, resolving the ref hop`() {
    val table = table(
      """
      queries:
        k: { entity: Konzert, fields: [datum, band, band.genre, preis, besucher, jahr], sort: [datum] }
      tabs:
        - name: T
          rows: [[{ table: Konzerte, query: k, columns: [datum, band, band.genre, { field: preis, label: Preis }, besucher, jahr] }]]
      """,
    )

    assertThat(table.type).isEqualTo(ReportPanelType.TABLE)
    assertThat(table.columns.map { it.label }).containsExactly("datum", "band", "band.genre", "Preis", "besucher", "jahr")
    assertThat(table.columns[3].unit).isEqualTo(ReportUnitSpec("CURRENCY", "CENTS", "ct"))
    assertThat(table.columns[0].type).isEqualTo("DATE")
    assertThat(table.texts()).containsExactly(
      listOf("20.01.2024", "Die Ärzte", "Punk", "30,00€", "100", "2024"),
      listOf("10.03.2025", "Die Ärzte", "Punk", "45,50€", "900", "2025"),
      listOf("02.11.2025", "Muse", "Rock", "120,00€", "15.000", "2025"),
      listOf("01.05.2026", "Muse", "Rock", "", "20.000", "2026"),
    )
    assertThat(table.rows[0][3].value).isEqualTo("3000")
    assertThat(table.totalRows).isEqualTo(4)
  }

  @Test
  fun `filters with the import filter rules, also on the ref hop`() {
    val table = table(
      """
      queries:
        k:
          entity: Konzert
          fields: [datum, ort]
          filter:
            - { field: ort, op: EQUALS, value: Hamburg }
            - { field: band.name, op: MATCHES, value: "^Die", mode: EXCLUDE }
      tabs:
        - name: T
          rows: [[{ table: X, query: k }]]
      """,
    )

    assertThat(table.texts()).containsExactly(listOf("01.05.2026", "Hamburg"))
  }

  @Test
  fun `numeric filters compare numbers`() {
    val table = table(
      """
      queries:
        k: { entity: Konzert, fields: [besucher], filter: [{ field: besucher, op: GREATER_THAN, value: 1000 }], sort: [besucher] }
      tabs:
        - name: T
          rows: [[{ table: X, query: k }]]
      """,
    )

    assertThat(table.texts()).containsExactly(listOf("15.000"), listOf("20.000"))
  }

  @Test
  fun `relative time range is resolved against today`() {
    val table = table(
      """
      queries:
        k: { entity: Konzert, fields: [datum], timeField: datum, range: last_12_months, sort: [datum] }
      tabs:
        - name: T
          rows: [[{ table: X, query: k }]]
      """,
    )

    assertThat(table.texts()).containsExactly(listOf("02.11.2025"), listOf("01.05.2026"))
  }

  @Test
  fun `fixed time range keeps both bounds`() {
    val table = table(
      """
      queries:
        k: { entity: Konzert, fields: [datum], timeField: datum, range: { from: 2025-03-10, to: 2025-11-02 }, sort: [datum] }
      tabs:
        - name: T
          rows: [[{ table: X, query: k }]]
      """,
    )

    assertThat(table.texts()).containsExactly(listOf("10.03.2025"), listOf("02.11.2025"))
  }

  @Test
  fun `sorts descending with empty values last, then limits`() {
    val table = table(
      """
      queries:
        k: { entity: Konzert, fields: [preis], sort: [{ field: preis, direction: DESC }], limit: 3 }
      tabs:
        - name: T
          rows: [[{ table: X, query: k }]]
      """,
    )

    assertThat(table.texts()).containsExactly(listOf("120,00€"), listOf("45,50€"), listOf("30,00€"))
  }

  @Test
  fun `without sort the newest objects come first and the panel limit applies`() {
    val table = table(
      """
      queries:
        k: { entity: Konzert, fields: [datum] }
      tabs:
        - name: T
          rows: [[{ table: X, query: k, limit: 2 }]]
      """,
    )

    assertThat(table.texts()).containsExactly(listOf("20.01.2024"), listOf("01.05.2026"))
    assertThat(table.totalRows).isEqualTo(4)
    assertThat(table.truncated).isFalse()
  }

  @Test
  fun `default columns are all non-object properties and computed properties`() {
    val table = table(
      """
      queries:
        k: { entity: Band }
      tabs:
        - name: T
          rows: [[{ table: X, query: k }]]
      """,
    )

    assertThat(table.columns.map { it.key }).containsExactly("name", "genre")
  }

  @Test
  fun `computed properties are only computed when used`() {
    table(
      """
      queries:
        k: { entity: Konzert, fields: [ort] }
      tabs:
        - name: T
          rows: [[{ table: X, query: k }]]
      """,
    )

    verify(exactly = 0) { computedProperty.computeValues(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `only the owner can render a report of the installation`() {
    withReport("tabs: []")

    assertThat(service.renderReport("mallory", "inst-1", "r-1").leftOrNull()).isEqualTo(ReportError.INSTALLED_APP_NOT_FOUND)
    assertThat(service.renderReport("alice", "inst-1", "unknown").leftOrNull()).isEqualTo(ReportError.REPORT_NOT_FOUND)
  }

  @Test
  fun `preview renders an unsaved definition and reports validation errors`() {
    withReport("tabs: []")

    val preview = service.previewReport("alice", "inst-1", "Entwurf", "tabs:\n  - name: Neu\n").getOrNull()!!
    val invalid = service.previewReport("alice", "inst-1", "Entwurf", "tabs: x").leftOrNull()

    assertThat(preview.name).isEqualTo("Entwurf")
    assertThat(preview.tabs.single().name).isEqualTo("Neu")
    assertThat(invalid).isInstanceOf(InvalidReportDefinitionError::class.java)
  }
}
