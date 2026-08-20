package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@QuarkusTest
@TestSecurity(user = "test-nav-user", roles = ["DEVELOPER"])
class UserAppDetailPageTests {

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @Inject
  lateinit var installedAppRepository: InstalledAppRepositoryPort

  @Inject
  lateinit var appDataRepository: AppDataRepositoryPort

  @BeforeEach
  fun setup() {
    if (userRepository.findByUsername(Username("test-nav-user")) == null) {
      userRepository.save(
        User(
          id = UserId(UUID.randomUUID().toString()),
          username = Username("test-nav-user"),
          passwordHash = "test-hash",
          roles = setOf(UserRole.DEVELOPER),
          createdAt = Instant.now(),
        ),
      )
    }
  }

  private fun createApp(appName: String): Pair<String, String> {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", appName)
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    val versionId = given()
      .`when`()
      .post("/ui/developer/apps/$appId/versions")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    return appId to versionId
  }

  private fun addEntity(appId: String, versionId: String, name: String): String =
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", name)
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

  private fun publishAndInstall(appId: String, appName: String): String {
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("bumpType", "BUGFIX")
      .formParam("releaseNotes", "Initial release")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/publish")
      .then()
      .statusCode(200)

    given()
      .`when`()
      .post("/ui/user/app-store/apps/$appId/install")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    val dashboardHtml = given()
      .`when`()
      .get("/ui/user/dashboard")
      .then()
      .statusCode(200)
      .extract().body().asString()

    return Regex("""href="/ui/user/apps/([^"]+)"[^>]*aria-label="App ${Regex.escape(appName)} öffnen"""")
      .find(dashboardHtml)?.groupValues?.get(1) ?: ""
  }

  private fun createDataAndGetId(installedAppId: String, entityId: String): String {
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("entityTypeId", entityId)
      .`when`()
      .post("/ui/user/apps/$installedAppId/data")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    return Regex("""data-href="/ui/user/apps/$installedAppId/data/([^"]+)"""").find(html)?.groupValues?.get(1) ?: ""
  }

  @Test
  fun `app detail page with single entity shows its data table directly without tabs`() {
    val appName = "Single Entity App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val entityId = addEntity(appId, versionId, "Entity One")
    val installedAppId = publishAndInstall(appId, appName)

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"entity-heading\""), "Expected the single entity's table to be rendered directly")
    assertTrue(html.contains("data/new?entityId=$entityId"), "Expected the add button to link to the new-data form for the single entity")
    assertTrue(!html.contains("data-testid=\"entity-tiles\""), "Expected no tile overview when there is only one entity")
    assertTrue(!html.contains("data-testid=\"entity-tabs\""), "Expected no tab navigation to be rendered")
  }

  @Test
  fun `app detail page with multiple entities shows tile overview linking to entity pages`() {
    val appName = "Multi Entity App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val entity1Id = addEntity(appId, versionId, "Entity One")
    val entity2Id = addEntity(appId, versionId, "Entity Two")
    val installedAppId = publishAndInstall(appId, appName)

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"entity-tiles\""), "Expected a tile overview for multiple entities")
    assertTrue(html.contains("/ui/user/apps/$installedAppId/entities/$entity1Id"), "Expected a tile linking to entity one's page")
    assertTrue(html.contains("/ui/user/apps/$installedAppId/entities/$entity2Id"), "Expected a tile linking to entity two's page")
    assertTrue(!html.contains("data-testid=\"entity-tabs\""), "Expected no tab navigation to be rendered")
  }

  @Test
  fun `entity detail page shows breadcrumbs and data table for the selected entity`() {
    val appName = "Entity Nav App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val entity1Id = addEntity(appId, versionId, "Entity One")
    addEntity(appId, versionId, "Entity Two")
    val installedAppId = publishAndInstall(appId, appName)

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/entities/$entity1Id")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"breadcrumb-entity\""), "Expected an entity breadcrumb entry")
    assertTrue(html.contains("Entity One"), "Expected the entity name to be rendered")
    assertTrue(html.contains("data/new?entityId=$entity1Id"), "Expected the add button to link to the new-data form for this entity")
  }

  @Test
  fun `entity detail page redirects to app detail for unknown entity id`() {
    val appName = "Unknown Entity App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    addEntity(appId, versionId, "Entity One")
    val installedAppId = publishAndInstall(appId, appName)

    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/user/apps/$installedAppId/entities/unknown-entity-id")
      .then()
      .statusCode(303)
      .header("Location", containsString("/ui/user/apps/$installedAppId"))
  }

  @Test
  fun `app detail page has a top row delete button for the installed app`() {
    val appName = "Deletable App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    addEntity(appId, versionId, "Entity One")
    val installedAppId = publishAndInstall(appId, appName)

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"delete-installed-app-button\""), "Expected a top-row delete button for the installed app")
  }

  @Test
  fun `new data page breadcrumb includes the entity name`() {
    val appName = "New Data Breadcrumb App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val entityId = addEntity(appId, versionId, "Entity One")
    val installedAppId = publishAndInstall(appId, appName)

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/data/new?entityId=$entityId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    val breadcrumbEntityLink = Regex("""data-testid="breadcrumb-entity-link">([^<]*)<""").find(html)?.groupValues?.get(1)?.trim()
    assertTrue(breadcrumbEntityLink == "Entity One", "Expected breadcrumb to show the entity name, but was: $breadcrumbEntityLink")
  }

  @Test
  fun `new data page has a header button to toggle multi create mode`() {
    val appName = "Multi Mode App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val entityId = addEntity(appId, versionId, "Entity One")
    val installedAppId = publishAndInstall(appId, appName)

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/data/new?entityId=$entityId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"mode-multi-button\""), "Expected a header button to toggle multi create mode")
  }

  @Test
  fun `new data page has header buttons to create and delete a snapshot`() {
    val appName = "Snapshot Mode App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val entityId = addEntity(appId, versionId, "Entity One")
    val installedAppId = publishAndInstall(appId, appName)

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/data/new?entityId=$entityId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"mode-snapshot-button\""), "Expected a header button to create/replace a snapshot")
    assertTrue(html.contains("data-testid=\"mode-snapshot-delete-button\""), "Expected a header button to delete a snapshot")
  }

  @Test
  fun `new data page has a header button to toggle focus mode`() {
    val appName = "Focus Mode App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val entityId = addEntity(appId, versionId, "Entity One")
    val installedAppId = publishAndInstall(appId, appName)

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/data/new?entityId=$entityId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"mode-focus-button\""), "Expected a header button to toggle focus mode")
  }

  @Test
  fun `edit data page breadcrumb includes both the entity name and the display text`() {
    val appName = "Edit Breadcrumb App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val entityId = addEntity(appId, versionId, "Entity One")
    val installedAppId = publishAndInstall(appId, appName)
    val dataId = createDataAndGetId(installedAppId, entityId)

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/data/$dataId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    val breadcrumbEntityLink = Regex("""data-testid="breadcrumb-entity-link">([^<]*)<""").find(html)?.groupValues?.get(1)?.trim()
    assertTrue(breadcrumbEntityLink == "Entity One", "Expected breadcrumb to show the entity name, but was: $breadcrumbEntityLink")

    val breadcrumbData = Regex("""data-testid="breadcrumb-data">([^<]*)<""").find(html)?.groupValues?.get(1)?.trim()
    assertTrue(breadcrumbData != "Daten bearbeiten", "Expected breadcrumb to show the entry's display text, but was: $breadcrumbData")
  }

  @Test
  fun `edit data page shows the app version metadata field below the last modified field`() {
    val appName = "App Version App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val entityId = addEntity(appId, versionId, "Entity One")
    val installedAppId = publishAndInstall(appId, appName)
    val dataId = createDataAndGetId(installedAppId, entityId)

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/data/$dataId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    val lastModifiedIndex = html.indexOf("data-testid=\"detail-last-modified\"")
    val appVersionIndex = html.indexOf("data-testid=\"detail-app-version\"")
    assertTrue(lastModifiedIndex >= 0 && appVersionIndex > lastModifiedIndex, "Expected the app version field to be rendered after the last modified field")

    val appVersion = Regex("""data-testid="detail-app-version">([^<]*)<""").find(html)?.groupValues?.get(1)?.trim()
    assertTrue(!appVersion.isNullOrBlank(), "Expected the app version metadata field to be rendered, but was: $appVersion")
  }

  @Test
  fun `saving edited data redirects back to the entity list instead of staying on the edit page`() {
    val appName = "Save Redirect App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val entityId = addEntity(appId, versionId, "Entity One")
    val installedAppId = publishAndInstall(appId, appName)
    val dataId = createDataAndGetId(installedAppId, entityId)

    given()
      .`when`()
      .post("/ui/user/apps/$installedAppId/data/$dataId")
      .then()
      .statusCode(200)
      .body(containsString("\"redirectUrl\":\"/ui/user/apps/$installedAppId\""))
  }

  @Test
  fun `app detail page and dashboard show deactivated info when the app is deactivated`() {
    val appName = "Deactivated Banner App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    addEntity(appId, versionId, "Entity One")
    val installedAppId = publishAndInstall(appId, appName)

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/deactivate")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    val detailHtml = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(200)
      .extract().body().asString()
    assertTrue(detailHtml.contains("data-testid=\"app-deactivated-banner\""), "Expected the deactivated banner on the app detail page")

    val dashboardHtml = given()
      .`when`()
      .get("/ui/user/dashboard")
      .then()
      .statusCode(200)
      .extract().body().asString()
    assertTrue(dashboardHtml.contains("data-testid=\"installed-app-deactivated-info\""), "Expected the deactivated info on the dashboard tile")
  }

  /**
   * Uninstalling now only enqueues an outbox event (see ADR 0019) instead of deleting synchronously, so the actual
   * removal - the last step of `UserAppStoreService.handle` - is observed by polling the dedicated status endpoint
   * instead of asserted immediately after the delete call returns. The outbox worker starts at application startup
   * and is signalled on enqueue, so it picks up the task well within this bound under normal test conditions.
   */
  private fun awaitUninstalled(installedAppId: String) {
    val deadlineMs = System.currentTimeMillis() + 5000
    while (System.currentTimeMillis() < deadlineMs) {
      val stillInstalled = given()
        .`when`()
        .get("/ui/user/apps/$installedAppId/status")
        .then()
        .statusCode(200)
        .extract().body().jsonPath().getBoolean("stillInstalled")
      if (!stillInstalled) return
      Thread.sleep(50)
    }
    throw AssertionError("Expected installed app $installedAppId to be uninstalled by the outbox dispatcher within 5s")
  }

  @Test
  fun `deleting an installed app enqueues its removal and it disappears in the background`() {
    val appName = "Uninstall App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    addEntity(appId, versionId, "Entity One")
    val installedAppId = publishAndInstall(appId, appName)

    given()
      .`when`()
      .post("/ui/user/apps/$installedAppId/delete")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    awaitUninstalled(installedAppId)

    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(303)
      .header("Location", containsString("/ui/user/dashboard"))
  }

  /**
   * A test installation pinned to a DRAFT version (issue #635) must be reachable through the same generic CRUD pages
   * as a real installation, and manually-entered data must coexist with data generated via the test data generator
   * (issues #636/#637/#646/#647).
   */
  @Test
  fun `test installation pinned to a draft version is reachable through the generic app detail page and mixes manual with generated data`() {
    val appName = "Test Installation Reach App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val entityId = addEntity(appId, versionId, "Entity One")

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("versionId", versionId)
      .`when`()
      .post("/ui/developer/apps/$appId/test-installations")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    val installedAppId = installedAppRepository.findAllByAppId(AppId(appId)).single().id.value

    val detailHtml = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(200)
      .extract().body().asString()
    assertTrue(detailHtml.contains("data-testid=\"test-installation-badge\""), "Expected the test installation badge to be rendered")
    assertTrue(detailHtml.contains("Entity One"), "Expected the entity to be rendered for the draft-pinned test installation")

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("installedAppId", installedAppId)
      .formParam("count", "3")
      .formParam("seed", "42")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/generate-test-data")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    createDataAndGetId(installedAppId, entityId)

    assertEquals(4, appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId(installedAppId), EntityDefinitionId(entityId)).size)
  }
}
