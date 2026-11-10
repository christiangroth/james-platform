package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.`in`.app.AppDataPort
import de.chrgroth.james.platform.domain.port.`in`.app.AppManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.AppVersionManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.DeveloperTestInstallationPort
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Declarative reports in the UI: report editor (save with validation, preview against a test installation) and the User's report page. */
@QuarkusTest
@TestSecurity(user = ReportPageTests.USERNAME, roles = ["USER", "DEVELOPER"])
class ReportPageTests {

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @Inject
  lateinit var appManagement: AppManagementPort

  @Inject
  lateinit var appVersionManagement: AppVersionManagementPort

  @Inject
  lateinit var userAppStore: UserAppStorePort

  @Inject
  lateinit var appData: AppDataPort

  @Inject
  lateinit var testInstallations: DeveloperTestInstallationPort

  @BeforeEach
  fun setup() {
    if (userRepository.findByUsername(Username(USERNAME)) == null) {
      userRepository.save(
        User(
          id = UserId(UUID.randomUUID().toString()),
          username = Username(USERNAME),
          passwordHash = "test-hash",
          roles = setOf(UserRole.USER, UserRole.DEVELOPER),
          createdAt = Instant.now(),
        ),
      )
    }
  }

  private fun developerId(): String = userRepository.findByUsername(Username(USERNAME))!!.id.value

  private data class Draft(val appId: String, val versionId: String, val entityId: String, val nameId: String, val datumId: String, val reportId: String)

  /** A draft with entity "Konzert" (name, datum) and the report "Konzerte" using [DEFINITION]. */
  private fun createDraft(): Draft {
    val appId = appManagement.createApp("Konzerte ${UUID.randomUUID()}", null, developerId()).getOrNull()!!.id.value
    val versionId = appVersionManagement.createVersion(appId).getOrNull()!!.id.value
    val entityId = appVersionManagement.addEntity(appId, versionId, "Konzert").getOrNull()!!.entityDefinitions.single().id.value
    appVersionManagement.addProperty(appId, versionId, entityId, "name", "STRING", nullable = true).getOrNull()!!
    val version = appVersionManagement.addProperty(appId, versionId, entityId, "datum", "DATE", nullable = true).getOrNull()!!
    val properties = version.entityDefinitions.single().properties
    val reportId = appVersionManagement.addReport(appId, versionId, "Konzerte", DEFINITION).getOrNull()!!.reports.single().id.value
    return Draft(appId, versionId, entityId, properties.first { it.name == "name" }.id.value, properties.first { it.name == "datum" }.id.value, reportId)
  }

  private fun addKonzert(draft: Draft, installedAppId: String, name: String, datum: String) {
    appData.createAppData(USERNAME, installedAppId, draft.entityId, mapOf("prop_${draft.nameId}" to listOf(name), "prop_${draft.datumId}" to listOf(datum)))
      .getOrNull()!!
  }

  @Test
  fun `report editor shows the yaml definition and the preview hint without test installation`() {
    val draft = createDraft()

    given().`when`().get("/ui/developer/apps/${draft.appId}/versions/${draft.versionId}/reports/${draft.reportId}")
      .then()
      .statusCode(200)
      .body(containsString("data-testid=\"report-definition-input\""))
      .body(containsString("konzerte: { entity: Konzert"))
      .body(containsString("data-testid=\"report-preview-no-installation\""))
      .body(containsString("/report-renderer.js"))
  }

  @Test
  fun `saving a report renames it and stores a valid definition`() {
    val draft = createDraft()

    given().contentType("application/x-www-form-urlencoded")
      .formParam("name", "Alle Konzerte")
      .formParam("definition", "tabs:\n  - name: Neu\n")
      .`when`().post("/ui/developer/apps/${draft.appId}/versions/${draft.versionId}/reports/${draft.reportId}")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val report = appVersionManagement.getVersion(draft.appId, draft.versionId).getOrNull()!!.reports.single()
    assertThat(report.name).isEqualTo("Alle Konzerte")
    assertThat(report.definition).isEqualTo("tabs:\n  - name: Neu\n")
  }

  @Test
  fun `saving an invalid definition lists the errors with line and keeps the stored definition`() {
    val draft = createDraft()

    given().contentType("application/x-www-form-urlencoded")
      .formParam("name", "Konzerte")
      .formParam("definition", "queries:\n  q:\n    entity: Lauf\ntabs: []\n")
      .`when`().post("/ui/developer/apps/${draft.appId}/versions/${draft.versionId}/reports/${draft.reportId}")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
      .body("errors[0].code", equalTo("ENTITY_NOT_FOUND"))
      .body("errors[0].line", equalTo(3))
      .body("errors[0].message", containsString("Lauf"))

    val report = appVersionManagement.getVersion(draft.appId, draft.versionId).getOrNull()!!.reports.single()
    assertThat(report.definition).isEqualTo(DEFINITION + "\n")
  }

  @Test
  fun `preview renders an unsaved definition against a test installation of the draft`() {
    val draft = createDraft()
    val installedAppId = testInstallations.createTestInstallation(draft.appId, draft.versionId, developerId(), USERNAME).getOrNull()!!.id.value
    addKonzert(draft, installedAppId, "Die Ärzte", "2026-05-01")
    addKonzert(draft, installedAppId, "Muse", "2025-11-02")

    given().contentType("application/x-www-form-urlencoded")
      .formParam("installedAppId", installedAppId)
      .formParam("name", "Konzerte")
      .formParam("definition", DEFINITION)
      .`when`().post("/ui/developer/apps/${draft.appId}/versions/${draft.versionId}/reports/${draft.reportId}/preview")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
      .body("view.tabs[0].name", equalTo("Übersicht"))
      .body("view.tabs[0].rows[0].panels[0].type", equalTo("text"))
      .body("view.tabs[0].rows[0].panels[1].type", equalTo("table"))
      .body("view.tabs[0].rows[0].panels[1].rows[0][0].text", equalTo("01.05.2026"))
      .body("view.tabs[0].rows[0].panels[1].rows[1][1].text", equalTo("Muse"))
  }

  @Test
  fun `preview rejects an installation that is no test installation of the draft`() {
    val draft = createDraft()

    given().contentType("application/x-www-form-urlencoded")
      .formParam("installedAppId", "foreign")
      .formParam("definition", DEFINITION)
      .`when`().post("/ui/developer/apps/${draft.appId}/versions/${draft.versionId}/reports/${draft.reportId}/preview")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
      .body("view", equalTo(null))
  }

  @Test
  fun `installed app lists its reports and the report page embeds the executed panel spec`() {
    val draft = createDraft()
    appVersionManagement.publishVersion(draft.appId, "BUGFIX", "Initial release").getOrNull()!!
    val installedAppId = userAppStore.installApp(USERNAME, draft.appId).getOrNull()!!.id.value
    addKonzert(draft, installedAppId, "Die Ärzte", "2026-05-01")

    val appPage = given().`when`().get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(200)
      .body(containsString("data-testid=\"report-tile\""))
      .body(containsString("/ui/user/apps/$installedAppId/reports/${draft.reportId}"))
      .extract().asString()
    // The reports come first, above the entities, without a heading of their own (the report icon says it).
    assertThat(appPage.indexOf("data-testid=\"app-reports\"")).isLessThan(appPage.indexOf("data-testid=\"entity-heading\""))
    assertThat(appPage).doesNotContain(">Reports</h2>")

    given().`when`().get("/ui/user/apps/$installedAppId/reports/${draft.reportId}")
      .then()
      .statusCode(200)
      .body(containsString("data-testid=\"report-view\""))
      .body(containsString("\"text\":\"01.05.2026\""))
      .body(containsString("/webjars/echarts/dist/echarts.min.js"))
      .body(not(containsString("data-testid=\"report-errors\"")))
  }

  @Test
  fun `report page embeds stat, timeseries and bar panels of aggregate queries`() {
    val draft = createDraft()
    val reportId = appVersionManagement.addReport(draft.appId, draft.versionId, "Statistik", CHART_DEFINITION).getOrNull()!!
      .reports.first { it.name == "Statistik" }.id.value
    appVersionManagement.publishVersion(draft.appId, "BUGFIX", "Initial release").getOrNull()!!
    val installedAppId = userAppStore.installApp(USERNAME, draft.appId).getOrNull()!!.id.value
    addKonzert(draft, installedAppId, "Die Ärzte", "2026-04-10")
    addKonzert(draft, installedAppId, "Muse", "2026-05-01")
    addKonzert(draft, installedAppId, "Muse", "2026-05-20")

    given().`when`().get("/ui/user/apps/$installedAppId/reports/$reportId")
      .then()
      .statusCode(200)
      .body(not(containsString("data-testid=\"report-errors\"")))
      .body(containsString("\"type\":\"stat\""))
      .body(containsString("\"text\":\"3\""))
      .body(containsString("\"type\":\"timeseries\""))
      .body(containsString("\"categories\":[\"04/2026\",\"05/2026\"]"))
      .body(containsString("\"type\":\"bar\""))
      .body(containsString("\"categories\":[\"Die Ärzte\",\"Muse\"]"))
      .body(containsString("data-text-stat-previous=\"ggü. Vorperiode\""))
      .body(containsString("data-text-chart-unavailable"))
  }

  @Test
  fun `report page renders variables and executes the report with the selection from the query parameters`() {
    val draft = createDraft()
    val reportId = appVersionManagement.addReport(draft.appId, draft.versionId, "Variablen", VARIABLE_DEFINITION).getOrNull()!!
      .reports.first { it.name == "Variablen" }.id.value
    appVersionManagement.publishVersion(draft.appId, "BUGFIX", "Initial release").getOrNull()!!
    val installedAppId = userAppStore.installApp(USERNAME, draft.appId).getOrNull()!!.id.value
    addKonzert(draft, installedAppId, "Die Ärzte", "2025-04-10")
    addKonzert(draft, installedAppId, "Muse", "2026-05-01")
    addKonzert(draft, installedAppId, "Muse", "2026-05-20")

    given().`when`().get("/ui/user/apps/$installedAppId/reports/$reportId")
      .then()
      .statusCode(200)
      .body(not(containsString("data-testid=\"report-errors\"")))
      .body(containsString("\"name\":\"jahr\""))
      .body(containsString("\"type\":\"select\""))
      .body(containsString("\"selected\":\"2026\""))
      .body(containsString("\"text\":\"2\""))
      .body(containsString("data-text-variables=\"Report-Variablen\""))

    given().queryParam("var.jahr", "2025").`when`().get("/ui/user/apps/$installedAppId/reports/$reportId")
      .then()
      .statusCode(200)
      .body(containsString("\"selected\":\"2025\""))
      .body(containsString("\"text\":\"1\""))
  }

  @Test
  fun `preview runs with the variable selections and invalid transforms are reported in German`() {
    val draft = createDraft()
    val installedAppId = testInstallations.createTestInstallation(draft.appId, draft.versionId, developerId(), USERNAME).getOrNull()!!.id.value
    addKonzert(draft, installedAppId, "Die Ärzte", "2025-04-10")
    addKonzert(draft, installedAppId, "Muse", "2026-05-01")

    given().contentType("application/x-www-form-urlencoded")
      .formParam("installedAppId", installedAppId)
      .formParam("name", "Variablen")
      .formParam("definition", VARIABLE_DEFINITION)
      .formParam("variables", "{\"jahr\":\"2025\"}")
      .`when`().post("/ui/developer/apps/${draft.appId}/versions/${draft.versionId}/reports/${draft.reportId}/preview")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
      .body("view.variables[0].selected", equalTo("2025"))
      .body("view.variables[0].options.value", contains("2025", "2026"))
      .body("view.tabs[0].rows[0].panels[0].text", equalTo("1"))

    given().contentType("application/x-www-form-urlencoded")
      .formParam("name", "Variablen")
      .formParam("definition", "queries:\n  q: { entity: Konzert, fields: [datum] }\ntransforms:\n  q: [{ calc: x, expr: \"1 +\" }]\ntabs: []\n")
      .`when`().post("/ui/developer/apps/${draft.appId}/versions/${draft.versionId}/reports/${draft.reportId}")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
      .body("errors.code", contains("EXPRESSION_INVALID"))
      .body("errors[0].message", containsString("Ungültiger Ausdruck"))

    given().contentType("application/x-www-form-urlencoded")
      .formParam("name", "Variablen")
      .formParam("definition", "queries:\n  q: { entity: Konzert, fields: [datum] }\ntransforms:\n  q: [{ calc: x, expr: \"datum * 2\" }]\ntabs: []\n")
      .`when`().post("/ui/developer/apps/${draft.appId}/versions/${draft.versionId}/reports/${draft.reportId}")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
      .body("errors.code", contains("EXPRESSION_OPERAND_TYPE"))
      .body("errors[0].message", containsString("nicht mit einem Wert vom Typ Datum"))
  }

  @Test
  fun `saving an invalid aggregate definition reports line and German message`() {
    val draft = createDraft()

    given().contentType("application/x-www-form-urlencoded")
      .formParam("name", "Konzerte")
      .formParam("definition", "queries:\n  q:\n    entity: Konzert\n    aggregate: { bucket: MONAT, sum: name }\ntabs: []\n")
      .`when`().post("/ui/developer/apps/${draft.appId}/versions/${draft.versionId}/reports/${draft.reportId}")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
      .body("errors.code", contains("TIME_FIELD_MISSING", "FIELD_NOT_NUMERIC"))
      .body("errors[1].line", equalTo(4))
      .body("errors[1].message", containsString("ist keine Zahl"))
  }

  @Test
  fun `preview lists the findings of checks in a warnings panel and counts them on the tab`() {
    val draft = createDraft()
    val installedAppId = testInstallations.createTestInstallation(draft.appId, draft.versionId, developerId(), USERNAME).getOrNull()!!.id.value
    addKonzert(draft, installedAppId, "Muse", "2026-01-10")
    addKonzert(draft, installedAppId, "Muse", "2026-03-10")

    given().contentType("application/x-www-form-urlencoded")
      .formParam("installedAppId", installedAppId)
      .formParam("name", "Prüfungen")
      .formParam("definition", CHECK_DEFINITION)
      .`when`().post("/ui/developer/apps/${draft.appId}/versions/${draft.versionId}/reports/${draft.reportId}/preview")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
      .body("view.tabs[0].warnings", equalTo(2))
      .body("view.tabs[0].rows[0].panels[0].type", equalTo("warnings"))
      .body("view.tabs[0].rows[0].panels[0].totalWarnings", equalTo(2))
      .body("view.tabs[0].rows[0].panels[0].warnings.label", contains("04/2026", "02/2026"))
      .body("view.tabs[0].rows[0].panels[0].warnings[0].message", equalTo("Kein Konzert im 04/2026"))
      .body("view.tabs[0].rows[0].panels[0].warnings[0].severity", equalTo("error"))
  }

  @Test
  fun `invalid checks are reported in German and the report page carries the warnings texts`() {
    val draft = createDraft()

    given().contentType("application/x-www-form-urlencoded")
      .formParam("name", "Konzerte")
      .formParam("definition", "queries:\n  q: { entity: Konzert, timeField: datum }\nchecks:\n  g: { gap: q, every: MONAT, message: \"{woche}\" }\ntabs: []\n")
      .`when`().post("/ui/developer/apps/${draft.appId}/versions/${draft.versionId}/reports/${draft.reportId}")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
      .body("errors.code", contains("MESSAGE_PLACEHOLDER_UNKNOWN"))
      .body("errors[0].message", containsString("Unbekannter Platzhalter „{woche}“"))

    appVersionManagement.publishVersion(draft.appId, "BUGFIX", "Initial release").getOrNull()!!
    val installedAppId = userAppStore.installApp(USERNAME, draft.appId).getOrNull()!!.id.value
    given().`when`().get("/ui/user/apps/$installedAppId/reports/${draft.reportId}")
      .then()
      .statusCode(200)
      .body(containsString("data-text-warnings-empty=\"Keine Warnungen.\""))
  }

  @Test
  fun `preview shows a stat goal with progress and a top-N table`() {
    val draft = createDraft()
    val installedAppId = testInstallations.createTestInstallation(draft.appId, draft.versionId, developerId(), USERNAME).getOrNull()!!.id.value
    addKonzert(draft, installedAppId, "Muse", "2026-01-10")
    addKonzert(draft, installedAppId, "Muse", "2026-03-10")
    addKonzert(draft, installedAppId, "Blur", "2026-03-12")

    given().contentType("application/x-www-form-urlencoded")
      .formParam("installedAppId", installedAppId)
      .formParam("name", "Ziele")
      .formParam("definition", GOAL_DEFINITION)
      .`when`().post("/ui/developer/apps/${draft.appId}/versions/${draft.versionId}/reports/${draft.reportId}/preview")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
      .body("view.tabs[0].rows[0].panels[0].goal.text", equalTo("4"))
      .body("view.tabs[0].rows[0].panels[0].goal.percentText", equalTo("50 %"))
      .body("view.tabs[0].rows[0].panels[1].rows[0][0].text", equalTo("03/2026"))
      .body("view.tabs[0].rows[0].panels[1].rows[0][1].value", equalTo("2"))
  }

  @Test
  fun `invalid advanced transformations are reported in German and the report page carries the goal text`() {
    val draft = createDraft()

    given().contentType("application/x-www-form-urlencoded")
      .formParam("name", "Konzerte")
      .formParam("definition", "queries:\n  q: { entity: Konzert, fields: [name] }\ntransforms:\n  q: [{ top: name }, { asOf: q, fields: [name], prefix: x_ }]\ntabs: []\n")
      .`when`().post("/ui/developer/apps/${draft.appId}/versions/${draft.versionId}/reports/${draft.reportId}")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
      .body("errors.code", contains("FIELD_NOT_NUMERIC", "TRANSFORM_QUERY_CYCLE", "TRANSFORM_NEEDS_TIME"))
      .body("errors[1].message", containsString("dürfen sich nicht gegenseitig"))
      .body("errors[2].message", containsString("braucht ein Datum pro Zeile"))

    appVersionManagement.publishVersion(draft.appId, "BUGFIX", "Initial release").getOrNull()!!
    val installedAppId = userAppStore.installApp(USERNAME, draft.appId).getOrNull()!!.id.value
    given().`when`().get("/ui/user/apps/$installedAppId/reports/${draft.reportId}")
      .then()
      .statusCode(200)
      .body(containsString("data-text-stat-goal=\"Ziel\""))
  }

  @Test
  fun `report page of an unknown report redirects to the installed app`() {
    val draft = createDraft()
    appVersionManagement.publishVersion(draft.appId, "BUGFIX", "Initial release").getOrNull()!!
    val installedAppId = userAppStore.installApp(USERNAME, draft.appId).getOrNull()!!.id.value

    given().redirects().follow(false).`when`().get("/ui/user/apps/$installedAppId/reports/unknown")
      .then()
      .statusCode(303)
      .header("Location", containsString("/ui/user/apps/$installedAppId"))
  }

  @Test
  fun `report renderer and echarts are served as static resources`() {
    given().`when`().get("/report-renderer.js").then().statusCode(200).body(containsString("JamesReports"))
    given().`when`().get("/webjars/echarts/dist/echarts.min.js").then().statusCode(200).body(notNullValue())
  }

  companion object {
    const val USERNAME = "report-page-user"

    val DEFINITION = """
      queries:
        konzerte: { entity: Konzert, fields: [datum, name], sort: [{ field: datum, direction: DESC }] }
      tabs:
        - name: Übersicht
          rows:
            - - { text: Hinweis, markdown: "Alle **Konzerte**" }
              - { table: Konzerte, query: konzerte }
    """.trimIndent()

    val CHART_DEFINITION = """
      queries:
        alle: { entity: Konzert }
        monate: { entity: Konzert, timeField: datum, aggregate: { bucket: MONAT, values: { konzerte: count } } }
        proBand: { entity: Konzert, aggregate: { groupBy: name, values: { konzerte: count } } }
      tabs:
        - name: Statistik
          rows:
            - - { stat: Konzerte, query: alle, reduce: count }
              - { timeseries: Pro Monat, query: monate, style: column }
              - { bar: Pro Band, query: proBand, horizontal: true }
    """.trimIndent()

    val GOAL_DEFINITION = """
      queries:
        monate: { entity: Konzert, timeField: datum, aggregate: { bucket: MONAT, values: { konzerte: count } } }
      transforms:
        monate: [{ top: konzerte }]
      tabs:
        - name: Ziele
          rows:
            - - { stat: Konzerte, query: monate, field: konzerte, reduce: sum, goal: 4 }
              - { table: Bester Monat, query: monate }
    """.trimIndent()

    val CHECK_DEFINITION = """
      queries:
        konzerte: { entity: Konzert, timeField: datum, range: { from: 2026-01-01, to: 2026-04-30 } }
      checks:
        monatlich: { gap: konzerte, every: MONAT, message: "Kein Konzert im {period}", severity: error }
      tabs:
        - name: Prüfungen
          rows:
            - - { warnings: Hinweise, checks: [monatlich] }
    """.trimIndent()

    val VARIABLE_DEFINITION = """
      variables:
        jahr: { type: select, label: Jahr, query: jahre, field: bucket, default: last }
      queries:
        jahre: { entity: Konzert, timeField: datum, aggregate: { bucket: JAHR, values: { konzerte: count } } }
        konzerte: { entity: Konzert, timeField: datum, range: ${'$'}jahr }
      transforms:
        jahre: [{ cumulative: konzerte, as: gesamt }]
      tabs:
        - name: Jahr
          rows:
            - - { stat: Konzerte, query: konzerte, reduce: count }
    """.trimIndent()
  }
}
