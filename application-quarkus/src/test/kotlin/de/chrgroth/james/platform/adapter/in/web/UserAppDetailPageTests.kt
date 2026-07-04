package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.containsString
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
  fun `deleting an installed app removes it and redirects to the dashboard`() {
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
      .body(containsString("\"redirectUrl\":\"/ui/user/dashboard\""))

    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(303)
      .header("Location", containsString("/ui/user/dashboard"))
  }
}
