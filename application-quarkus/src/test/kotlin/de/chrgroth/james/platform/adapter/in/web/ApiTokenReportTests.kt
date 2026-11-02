package de.chrgroth.james.platform.adapter.`in`.web

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

    val reportId = bearer(token).contentType("application/json").body(mapOf("name" to "Läufe", "definition" to DEFINITION))
      .`when`().post(base)
      .then().statusCode(200)
      .body("reports.name", hasItem("Läufe"))
      .extract().path<String>("reports[0].id")

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

  companion object {
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
