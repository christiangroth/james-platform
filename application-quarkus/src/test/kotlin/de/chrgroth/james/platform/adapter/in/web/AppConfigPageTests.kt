package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.app.AppVersion
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
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** The App configuration (ADR 0027): developer toggle (UI and token API), the user's "Einstellungen" and `config.*` in reports. */
@QuarkusTest
@TestSecurity(user = AppConfigPageTests.USERNAME, roles = ["USER", "DEVELOPER"])
class AppConfigPageTests {

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
  lateinit var apiTokens: ApiTokenPort

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

  private fun developer(): User = userRepository.findByUsername(Username(USERNAME))!!

  private data class Draft(val appId: String, val versionId: String, val laufId: String, val distanzId: String, val configId: String, val zielId: String)

  /** A draft with entity "Lauf" (distanz) and the not yet marked entity "Ziele" (zielKm, required, default 100). */
  private fun createDraft(): Draft {
    val appId = appManagement.createApp("Laufen ${UUID.randomUUID()}", null, developer().id.value).getOrNull()!!.id.value
    val versionId = appVersionManagement.createVersion(appId).getOrNull()!!.id.value
    val laufId = appVersionManagement.addEntity(appId, versionId, "Lauf").getOrNull()!!.entityDefinitions.single().id.value
    val withDistanz = appVersionManagement.addProperty(appId, versionId, laufId, "distanz", "LONG", nullable = true).getOrNull()!!
    val distanzId = withDistanz.entityDefinitions.single().properties.single().id.value
    val configId = appVersionManagement.addEntity(appId, versionId, "Ziele").getOrNull()!!.entityDefinitions.first { it.name == "Ziele" }.id.value
    val withZiel = appVersionManagement.addProperty(appId, versionId, configId, "zielKm", "LONG", nullable = false).getOrNull()!!
    val zielId = withZiel.entity(configId).properties.single().id.value
    appVersionManagement.setPropertyDefault(appId, versionId, configId, zielId, "100").getOrNull()!!
    return Draft(appId, versionId, laufId, distanzId, configId, zielId)
  }

  private fun AppVersion.entity(id: String) = entityDefinitions.first { it.id.value == id }

  private fun publishAndInstall(draft: Draft): String {
    appVersionManagement.setEntityConfiguration(draft.appId, draft.versionId, draft.configId, true).getOrNull()!!
    appVersionManagement.publishVersion(draft.appId, "BUGFIX", "Initial release").getOrNull()!!
    return userAppStore.installApp(USERNAME, draft.appId).getOrNull()!!.id.value
  }

  @Test
  fun `developer marks an entity as configuration in the version editor`() {
    val draft = createDraft()

    given().contentType("application/x-www-form-urlencoded").formParam("configuration", "true")
      .`when`().post("/ui/developer/apps/${draft.appId}/versions/${draft.versionId}/entities/${draft.configId}/configuration")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
      .body("message", equalTo("Konfiguration gespeichert."))

    given().contentType("application/x-www-form-urlencoded").formParam("configuration", "true")
      .`when`().post("/ui/developer/apps/${draft.appId}/versions/${draft.versionId}/entities/${draft.laufId}/configuration")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
      .body("message", equalTo("Diese Version hat bereits eine Konfigurations-Entität."))

    given().`when`().get("/ui/developer/apps/${draft.appId}/versions/${draft.versionId}/entities/${draft.configId}")
      .then()
      .statusCode(200)
      .body(containsString("data-testid=\"entity-configuration-switch\""))

    given().`when`().get("/ui/developer/apps/${draft.appId}/versions/${draft.versionId}")
      .then()
      .statusCode(200)
      .body(containsString("data-testid=\"entity-configuration-badge\""))
  }

  @Test
  fun `publishing a configuration with a required property without default fails`() {
    val draft = createDraft()
    appVersionManagement.setPropertyDefault(draft.appId, draft.versionId, draft.configId, draft.zielId, null).getOrNull()!!
    appVersionManagement.setEntityConfiguration(draft.appId, draft.versionId, draft.configId, true).getOrNull()!!

    given().contentType("application/x-www-form-urlencoded").formParam("bumpType", "BUGFIX").formParam("releaseNotes", "Initial")
      .`when`().post("/ui/developer/apps/${draft.appId}/versions/publish")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
      .body("message", containsString("Ungültige Konfiguration: Ziele"))
  }

  @Test
  fun `user edits the settings of an installation, which are no data tile and cannot be deleted`() {
    val draft = createDraft()
    val installedAppId = publishAndInstall(draft)

    given().`when`().get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(200)
      .body(containsString("data-testid=\"settings-button\""))
      .body(containsString("/ui/user/apps/$installedAppId/settings"))
      .body(not(containsString("/ui/user/apps/$installedAppId/entities/${draft.configId}")))
      .body(not(containsString("data-testid=\"entity-tiles\"")))

    val location = given().redirects().follow(false).`when`().get("/ui/user/apps/$installedAppId/settings")
      .then()
      .statusCode(303)
      .extract().header("Location")
    val dataId = location.substringAfterLast("/")
    assertThat(location).contains("/ui/user/apps/$installedAppId/data/")

    given().`when`().get("/ui/user/apps/$installedAppId/data/$dataId")
      .then()
      .statusCode(200)
      .body(containsString("data-testid=\"breadcrumb-settings\""))
      .body(containsString("value=\"100\""))
      .body(not(containsString("data-testid=\"delete-button\"")))

    given().contentType("application/x-www-form-urlencoded").formParam("prop_${draft.zielId}", "250")
      .`when`().post("/ui/user/apps/$installedAppId/data/$dataId")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
      .body("message", equalTo("Einstellungen gespeichert."))
      .body("redirectUrl", equalTo("/ui/user/apps/$installedAppId"))

    given().`when`().post("/ui/user/apps/$installedAppId/data/$dataId/delete")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
      .body("message", equalTo("Die Einstellungen können nicht gelöscht werden."))

    given().contentType("application/x-www-form-urlencoded").formParam("entityTypeId", draft.configId).formParam("prop_${draft.zielId}", "5")
      .`when`().post("/ui/user/apps/$installedAppId/data")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
      .body("message", equalTo("Die Einstellungen existieren bereits und können nur bearbeitet werden."))
  }

  @Test
  fun `reports read the configuration as config fields`() {
    val draft = createDraft()
    appVersionManagement.setEntityConfiguration(draft.appId, draft.versionId, draft.configId, true).getOrNull()!!
    val reportId = appVersionManagement.addReport(draft.appId, draft.versionId, "Ziele", REPORT).getOrNull()!!.reports.single().id.value
    val installedAppId = publishAndInstall(draft)
    appData.createAppData(USERNAME, installedAppId, draft.laufId, mapOf("prop_${draft.distanzId}" to listOf("150"))).getOrNull()!!

    given().`when`().get("/ui/user/apps/$installedAppId/reports/$reportId")
      .then()
      .statusCode(200)
      .body(not(containsString("data-testid=\"report-errors\"")))
      .body(containsString("\"text\":\"100\""))
      .body(containsString("\"color\":\"green\""))
  }

  @Test
  fun `developer token API sets the configuration flag`() {
    val draft = createDraft()
    val token = apiTokens.createToken(USERNAME, "config", setOf(ApiTokenScope.DEVELOPER_WRITE), emptySet()).getOrNull()!!.plainToken
    val url = "/api/v1/developer/apps/${draft.appId}/versions/${draft.versionId}/entities/${draft.configId}/configuration"

    given().header("Authorization", "Bearer $token").contentType("application/json").body(mapOf("value" to true))
      .`when`().put(url)
      .then()
      .statusCode(200)
      .body("entities.find { it.name == 'Ziele' }.configuration", equalTo(true))
      .body("entities.find { it.name == 'Lauf' }.configuration", equalTo(false))

    given().header("Authorization", "Bearer $token").contentType("application/json").body(mapOf("value" to true))
      .`when`().put("/api/v1/developer/apps/${draft.appId}/versions/${draft.versionId}/entities/${draft.laufId}/configuration")
      .then()
      .statusCode(400)
      .body("code", equalTo("APPVER-073"))

    given().header("Authorization", "Bearer $token").contentType("application/json").body(emptyMap<String, Any>())
      .`when`().put(url)
      .then()
      .statusCode(400)
  }

  companion object {
    const val USERNAME = "app-config-page-user"

    private val REPORT = """
      queries:
        laeufe: { entity: Lauf, aggregate: { sum: distanz } }
      tabs:
        - name: Ziele
          rows:
            - - { stat: Jahresziel, config: zielKm }
              - { stat: Gelaufen, query: laeufe, thresholds: [{ value: 0, color: red }, { value: config.zielKm, color: green }] }
    """.trimIndent()
  }
}
