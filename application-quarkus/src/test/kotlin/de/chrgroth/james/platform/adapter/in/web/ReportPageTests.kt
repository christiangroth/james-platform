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

    given().`when`().get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(200)
      .body(containsString("data-testid=\"report-tile\""))
      .body(containsString("/ui/user/apps/$installedAppId/reports/${draft.reportId}"))

    given().`when`().get("/ui/user/apps/$installedAppId/reports/${draft.reportId}")
      .then()
      .statusCode(200)
      .body(containsString("data-testid=\"report-view\""))
      .body(containsString("\"text\":\"01.05.2026\""))
      .body(containsString("/webjars/echarts/dist/echarts.min.js"))
      .body(not(containsString("data-testid=\"report-errors\"")))
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
  }
}
