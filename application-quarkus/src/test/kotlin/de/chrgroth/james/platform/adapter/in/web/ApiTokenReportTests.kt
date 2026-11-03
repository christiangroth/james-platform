package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.error.InvalidReportDefinitionError
import de.chrgroth.james.platform.domain.model.user.ApiTokenScope
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.`in`.app.AppDataPort
import de.chrgroth.james.platform.domain.port.`in`.app.AppManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.AppVersionManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import de.chrgroth.james.platform.domain.port.`in`.user.ApiTokenPort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Report endpoints of the personal access token API: Developer CRUD on a draft's reports, User execution of a report. */
@QuarkusTest
class ApiTokenReportTests {

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @Inject
  lateinit var apiTokens: ApiTokenPort

  @Inject
  lateinit var appManagement: AppManagementPort

  @Inject
  lateinit var appVersionManagement: AppVersionManagementPort

  @Inject
  lateinit var userAppStore: UserAppStorePort

  @Inject
  lateinit var appData: AppDataPort

  private fun createUser(): User {
    val user = User(
      id = UserId(UUID.randomUUID().toString()),
      username = Username("api-report-${UUID.randomUUID()}"),
      passwordHash = "hash",
      roles = setOf(UserRole.USER, UserRole.DEVELOPER),
      createdAt = Instant.now(),
    )
    userRepository.save(user)
    return user
  }

  private fun token(user: User, vararg scopes: ApiTokenScope): String = apiTokens.createToken(user.username.value, "test", scopes.toSet(), emptySet()).getOrNull()!!.plainToken

  private fun bearer(token: String): RequestSpecification = given().header("Authorization", "Bearer $token").redirects().follow(false)

  /** A draft with entity "Lauf" (distanz in meters); returns appId, versionId, entityId, distanz property id. */
  private fun createDraft(developer: User): List<String> {
    val appId = appManagement.createApp("Laufen ${UUID.randomUUID()}", null, developer.id.value).getOrNull()!!.id.value
    val versionId = appVersionManagement.createVersion(appId).getOrNull()!!.id.value
    val entityId = appVersionManagement.addEntity(appId, versionId, "Lauf").getOrNull()!!.entityDefinitions.single().id.value
    val version = appVersionManagement.addProperty(appId, versionId, entityId, "distanz", "LONG", nullable = true).getOrNull()!!
    return listOf(appId, versionId, entityId, version.entityDefinitions.single().properties.single().id.value)
  }

  @Test
  fun `developer write scope creates, lists, updates and deletes reports with yaml definitions`() {
    val developer = createUser()
    val token = token(developer, ApiTokenScope.DEVELOPER_WRITE)
    val (appId, versionId) = createDraft(developer)
    val base = "/api/v1/developer/apps/$appId/versions/$versionId/reports"

    bearer(token).contentType("application/json").body(mapOf("name" to "Läufe", "definition" to "queries:\n  l: { entity: Rennen }\ntabs: []\n"))
      .`when`().post(base)
      .then().statusCode(400)
      .body("code", equalTo("APPVER-071"))
      .body("details[0]", containsString("line 2"))
      .body("details[0]", containsString("ENTITY_NOT_FOUND"))

    val created = bearer(token).contentType("application/json").body(mapOf("name" to "Läufe", "definition" to DEFINITION))
      .`when`().post(base)
      .then().statusCode(200)
      .body("reports.name", hasItem("Läufe"))
      .extract()
    val reportId = created.path<String>("reports[0].id")
    assertThat(created.header("X-Report-Id")).isEqualTo(reportId)
    assertThat(created.header("Location")).endsWith("$base/$reportId")

    bearer(token).`when`().get(base)
      .then().statusCode(200)
      .body("$", hasSize<Any>(1))
      .body("[0].name", equalTo("Läufe"))
      .body("[0].definition", containsString("entity: Lauf"))

    bearer(token).contentType("application/json").body(mapOf("name" to "Alle Läufe", "definition" to "tabs: []"))
      .`when`().put("$base/$reportId")
      .then().statusCode(200)
      .body("reports[0].name", equalTo("Alle Läufe"))
      .body("reports[0].definition", equalTo("tabs: []\n"))

    bearer(token).`when`().delete("$base/$reportId")
      .then().statusCode(200)
      .body("reports", empty<Any>())
  }

  @Test
  fun `developer read scope cannot change reports`() {
    val developer = createUser()
    val token = token(developer, ApiTokenScope.DEVELOPER_READ)
    val (appId, versionId) = createDraft(developer)

    bearer(token).contentType("application/json").body(mapOf("name" to "X", "definition" to ""))
      .`when`().post("/api/v1/developer/apps/$appId/versions/$versionId/reports")
      .then().statusCode(403)
    bearer(token).`when`().get("/api/v1/developer/apps/$appId/versions/$versionId/reports").then().statusCode(200)
  }

  @Test
  fun `user read scope executes a report of an installation`() {
    val user = createUser()
    val (appId, versionId, entityId, distanzId) = createDraft(user)
    val reportId = appVersionManagement.addReport(appId, versionId, "Läufe", DEFINITION).getOrNull()!!.reports.single().id.value
    appVersionManagement.publishVersion(appId, "BUGFIX", "Initial release").getOrNull()!!
    val installedAppId = userAppStore.installApp(user.username.value, appId).getOrNull()!!.id.value
    appData.createAppData(user.username.value, installedAppId, entityId, mapOf("prop_$distanzId" to listOf("12345"))).getOrNull()!!
    val token = token(user, ApiTokenScope.USER_READ)

    bearer(token).`when`().get("/api/v1/user/apps/$installedAppId/reports/$reportId")
      .then().statusCode(200)
      .body("name", equalTo("Läufe"))
      .body("tabs[0].rows[0].panels[0].type", equalTo("table"))
      .body("tabs[0].rows[0].panels[0].columns[0].key", equalTo("distanz"))
      .body("tabs[0].rows[0].panels[0].rows[0][0].value", equalTo("12345"))
      .body("tabs[0].rows[0].panels[0].rows[0][0].text", equalTo("12.345"))

    bearer(token).`when`().get("/api/v1/user/apps/$installedAppId/reports/unknown")
      .then().statusCode(404).body("code", equalTo("REPORT-002"))
    bearer(token(createUser(), ApiTokenScope.USER_READ)).`when`().get("/api/v1/user/apps/$installedAppId/reports/$reportId")
      .then().statusCode(404)
  }

  @Test
  fun `user read scope executes stat and timeseries panels over aggregate queries`() {
    val user = createUser()
    val (appId, versionId, entityId, distanzId) = createDraft(user)
    val datumId = appVersionManagement.addProperty(appId, versionId, entityId, "datum", "DATE", nullable = true).getOrNull()!!
      .entityDefinitions.single().properties.first { it.name == "datum" }.id.value
    val reportId = appVersionManagement.addReport(appId, versionId, "Statistik", CHART_DEFINITION).getOrNull()!!.reports.single().id.value
    appVersionManagement.publishVersion(appId, "BUGFIX", "Initial release").getOrNull()!!
    val installedAppId = userAppStore.installApp(user.username.value, appId).getOrNull()!!.id.value
    listOf("2026-04-10" to "5000", "2026-05-01" to "12345", "2026-05-20" to "1000").forEach { (datum, distanz) ->
      appData.createAppData(user.username.value, installedAppId, entityId, mapOf("prop_$distanzId" to listOf(distanz), "prop_$datumId" to listOf(datum))).getOrNull()!!
    }

    bearer(token(user, ApiTokenScope.USER_READ)).`when`().get("/api/v1/user/apps/$installedAppId/reports/$reportId")
      .then().statusCode(200)
      .body("tabs[0].rows[0].panels[0].type", equalTo("stat"))
      .body("tabs[0].rows[0].panels[0].text", equalTo("13.345"))
      .body("tabs[0].rows[0].panels[0].color", equalTo("green"))
      .body("tabs[0].rows[0].panels[0].comparison.previousText", equalTo("5.000"))
      .body("tabs[0].rows[0].panels[0].comparison.deltaText", equalTo("+8.345"))
      .body("tabs[0].rows[0].panels[0].comparison.percentText", equalTo("+166,9 %"))
      .body("tabs[0].rows[0].panels[0].comparison.trend", equalTo("up"))
      .body("tabs[0].rows[0].panels[0].comparison.periodText", equalTo("01.04.2026 – 30.04.2026"))
      .body("tabs[0].rows[0].panels[1].type", equalTo("timeseries"))
      .body("tabs[0].rows[0].panels[1].xAxis.type", equalTo("category"))
      .body("tabs[0].rows[0].panels[1].xAxis.categories", contains("04/2026", "05/2026"))
      .body("tabs[0].rows[0].panels[1].yAxes.position", contains("left", "right"))
      .body("tabs[0].rows[0].panels[1].series.name", contains("km", "laeufe"))
      .body("tabs[0].rows[0].panels[1].series[0].style", equalTo("column"))
      .body("tabs[0].rows[0].panels[1].series[1].yAxis", equalTo(1))
      .body("tabs[0].rows[0].panels[1].series[1].points.y", contains(1.0f, 2.0f))
      .body("tabs[0].rows[0].panels[1].series[0].points[1].text", equalTo("13.345"))
  }

  @Test
  fun `user read scope executes pie and heatmap panels and a kotlin step`() {
    val user = createUser()
    val (appId, versionId, entityId, distanzId) = createDraft(user)
    appVersionManagement.addProperty(appId, versionId, entityId, "datum", "DATE", nullable = true).getOrNull()!!
    val properties = appVersionManagement.addProperty(appId, versionId, entityId, "strecke", "STRING", nullable = true).getOrNull()!!
      .entityDefinitions.single().properties
    val datumId = properties.first { it.name == "datum" }.id.value
    val streckeId = properties.first { it.name == "strecke" }.id.value
    val reportId = appVersionManagement.addReport(appId, versionId, "Verteilung", VISUAL_DEFINITION).getOrNull()!!.reports.single().id.value
    appVersionManagement.publishVersion(appId, "BUGFIX", "Initial release").getOrNull()!!
    val installedAppId = userAppStore.installApp(user.username.value, appId).getOrNull()!!.id.value
    listOf(Triple("2026-04-10", "5000", "A"), Triple("2026-05-01", "12345", "B"), Triple("2026-05-20", "1000", "A")).forEach { (datum, distanz, strecke) ->
      val values = mapOf("prop_$distanzId" to listOf(distanz), "prop_$datumId" to listOf(datum), "prop_$streckeId" to listOf(strecke))
      appData.createAppData(user.username.value, installedAppId, entityId, values).getOrNull()!!
    }

    bearer(token(user, ApiTokenScope.USER_READ)).`when`().get("/api/v1/user/apps/$installedAppId/reports/$reportId")
      .then().statusCode(200)
      .body("tabs[0].rows[0].panels[0].type", equalTo("pie"))
      .body("tabs[0].rows[0].panels[0].donut", equalTo(true))
      .body("tabs[0].rows[0].panels[0].slices.label", contains("B", "A"))
      .body("tabs[0].rows[0].panels[0].slices[0].percentText", equalTo("67,3 %"))
      .body("tabs[0].rows[0].panels[1].type", equalTo("heatmap"))
      .body("tabs[0].rows[0].panels[1].heatmap.mode", equalTo("calendar"))
      .body("tabs[0].rows[0].panels[1].heatmap.from", equalTo("2026-04-10"))
      .body("tabs[0].rows[0].panels[1].heatmap.to", equalTo("2026-05-20"))
      .body("tabs[0].rows[0].panels[1].heatmap.cells.x", contains("2026-04-10", "2026-05-01", "2026-05-20"))
      .body("tabs[0].rows[0].panels[1].heatmap.cells[1].text", equalTo("24.690"))
  }

  @Test
  fun `saving a report whose kotlin step does not compile is rejected`() {
    val user = createUser()
    val (appId, versionId) = createDraft(user)
    val definition = "queries:\n  laeufe: { entity: Lauf }\ntransforms:\n  laeufe: [{ kotlin: \"unbekannt(rows)\" }]\ntabs: []\n"

    val result = appVersionManagement.addReport(appId, versionId, "Kaputt", definition)

    assertThat(result.leftOrNull()).isInstanceOf(InvalidReportDefinitionError::class.java)
  }

  companion object {
    val CHART_DEFINITION = """
      queries:
        monate: { entity: Lauf, timeField: datum, aggregate: { bucket: MONAT, values: { km: { sum: distanz }, laeufe: count } } }
        mai: { entity: Lauf, timeField: datum, range: { from: 2026-05-01, to: 2026-05-31 }, fields: [datum, distanz] }
      tabs:
        - name: Übersicht
          rows:
            - - { stat: Mai, query: mai, field: distanz, reduce: sum, compare: previous_period, thresholds: [{ value: 0, color: red }, { value: 10000, color: green }] }
              - { timeseries: Monate, query: monate, series: [{ field: km, style: column }, { field: laeufe, axis: right }] }
    """.trimIndent()

    val VISUAL_DEFINITION = """
      queries:
        strecken: { entity: Lauf, aggregate: { groupBy: strecke, values: { km: { sum: distanz } } } }
        tage: { entity: Lauf, timeField: datum, fields: [datum, distanz] }
      transforms:
        tage:
          - kotlin: |
              rows.map { it + ("doppelt" to it["distanz"]!!.toLong() * 2) }
            fields: { doppelt: LONG }
      tabs:
        - name: Übersicht
          rows:
            - - { pie: Strecken, query: strecken, donut: true }
              - { heatmap: Tage, query: tage, field: doppelt }
    """.trimIndent()

    val DEFINITION = """
      queries:
        laeufe: { entity: Lauf }
      tabs:
        - name: Übersicht
          rows:
            - - { table: Läufe, query: laeufe }
    """.trimIndent()
  }
}
