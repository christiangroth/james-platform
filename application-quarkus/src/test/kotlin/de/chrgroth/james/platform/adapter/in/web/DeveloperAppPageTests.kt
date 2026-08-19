package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.VersionNumber
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
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@QuarkusTest
@TestSecurity(user = "test-developer", roles = ["DEVELOPER"])
class DeveloperAppPageTests {

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @Inject
  lateinit var installedAppRepository: InstalledAppRepositoryPort

  @Inject
  lateinit var appDataRepository: AppDataRepositoryPort

  @BeforeEach
  fun setup() {
    if (userRepository.findByUsername(Username("test-developer")) == null) {
      userRepository.save(
        User(
          id = UserId(UUID.randomUUID().toString()),
          username = Username("test-developer"),
          passwordHash = "test-hash",
          roles = setOf(UserRole.DEVELOPER),
          createdAt = Instant.now(),
        ),
      )
    }
  }

  @Test
  fun `developer dashboard displays breadcrumb apps grid and new-app tile`() {
    given()
      .`when`()
      .get("/ui/developer/dashboard")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="breadcrumb-developer""""))
      .body(containsString("""data-testid="apps-grid""""))
      .body(containsString("""data-testid="new-app-tile""""))
  }

  @Test
  fun `developer dashboard does not show static error message`() {
    given()
      .`when`()
      .get("/ui/developer/dashboard")
      .then()
      .statusCode(200)
      .body(not(containsString("""data-testid="error-message""")))
  }

  @Test
  fun `create app returns error json on blank name`() {
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .contentType(containsString("application/json"))
      .body(containsString("\"ok\":false"))
  }

  @Test
  fun `create app returns success json with redirect on valid name`() {
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Test App ${System.nanoTime()}")
      .formParam("description", "A test app")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .contentType(containsString("application/json"))
      .body(containsString("\"ok\":true"))
      .body(containsString("/ui/developer/apps/"))
  }

  @Test
  fun `app overview redirects to dashboard for unknown app`() {
    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/developer/apps/unknown-id")
      .then()
      .statusCode(303)
      .header("Location", containsString("/ui/developer/dashboard"))
  }

  @Test
  fun `version editor redirects to dashboard for unknown app`() {
    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/developer/apps/unknown-id/versions/unknown-version-id")
      .then()
      .statusCode(303)
      .header("Location", containsString("/ui/developer/dashboard"))
  }

  @Test
  fun `entity editor redirects to dashboard for unknown app`() {
    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/developer/apps/unknown-id/versions/unknown-version-id/entities/unknown-entity-id")
      .then()
      .statusCode(303)
      .header("Location", containsString("/ui/developer/dashboard"))
  }

  @Test
  fun `report editor redirects to dashboard for unknown app`() {
    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/developer/apps/unknown-id/versions/unknown-version-id/reports/unknown-report-id")
      .then()
      .statusCode(303)
      .header("Location", containsString("/ui/developer/dashboard"))
  }

  @Test
  fun `draft version page does not show status badge, version number placeholder, or created-at date`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Test UI App ${System.nanoTime()}")
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

    given()
      .`when`()
      .get("/ui/developer/apps/$appId/versions/$versionId")
      .then()
      .statusCode(200)
      .body(not(containsString("""data-testid="version-status"""")))
      .body(not(containsString("No version number yet")))
      .body(not(containsString("""data-testid="version-created-at"""")))
      .body(containsString("""data-testid="delete-draft-version-button""""))
      .body(containsString("""data-testid="publish-version-button""""))
  }

  @Test
  fun `published version page does not show status badge or readonly banner`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Test Published App ${System.nanoTime()}")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/versions")
      .then()
      .statusCode(200)

    val publishedVersionId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("bumpType", "BUGFIX")
      .formParam("releaseNotes", "Initial release")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/publish")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .`when`()
      .get("/ui/developer/apps/$appId/versions/$publishedVersionId")
      .then()
      .statusCode(200)
      .body(not(containsString("""data-testid="version-status"""")))
      .body(not(containsString("""data-testid="published-readonly-banner"""")))
      .body(containsString("""data-testid="version-number""""))
      .body(containsString("""data-testid="version-created-at""""))
  }

  @Test
  fun `entity editor shows sort order as text and edit button when entity has properties`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Sort Order UI App ${System.nanoTime()}")
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

    val entityId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "TestEntity")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Amount")
      .formParam("type", "LONG")
      .formParam("nullable", false)
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    given()
      .`when`()
      .get("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="sort-order-value""""))
      .body(containsString("""data-testid="open-sort-order-modal-button""""))
  }

  @Test
  fun `update entity sort criteria returns success`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Sort Criteria App ${System.nanoTime()}")
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

    val entityId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Order")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    val propertyId = run {
      given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("name", "Amount")
        .formParam("type", "LONG")
        .formParam("nullable", false)
        .`when`()
        .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties")
        .then()
        .statusCode(200)
        .body(containsString("\"ok\":true"))
      val entityHtml = given()
        .`when`()
        .get("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")
        .then()
        .statusCode(200)
        .extract().body().asString()
      Regex("""data-property-id="([^"]+)"""").find(entityHtml)?.groupValues?.get(1) ?: ""
    }

    given()
      .contentType("application/json")
      .body("""[{"propertyId": "$propertyId", "direction": "DESC"}]""")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/sort-criteria")
      .then()
      .statusCode(200)
      .contentType(containsString("application/json"))
      .body(containsString("\"ok\":true"))
  }

  @Test
  fun `update entity sort criteria with empty list clears sort order`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Sort Clear App ${System.nanoTime()}")
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

    val entityId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "ClearOrder")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .contentType("application/json")
      .body("[]")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/sort-criteria")
      .then()
      .statusCode(200)
      .contentType(containsString("application/json"))
      .body(containsString("\"ok\":true"))
  }

  @Test
  fun `list property list item type is persisted across reload`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "List Item Type App ${System.nanoTime()}")
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

    val entityId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "TestEntity")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Tags")
      .formParam("type", "LIST")
      .formParam("nullable", true)
      .formParam("listItemType", "STRING")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    given()
      .`when`()
      .get("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")
      .then()
      .statusCode(200)
      .body(containsString("&lt;Text&gt;"))
  }

  @Test
  fun `setPropertyUnit persists unit and is reflected on the edit-property page`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Unit App ${System.nanoTime()}")
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

    val entityId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "TestEntity")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    val propertyId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Distance")
      .formParam("type", "LONG")
      .formParam("nullable", false)
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("propertyId")

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("family", "DISTANCE")
      .formParam("storageGranularity", "METERS")
      .formParam("defaultGranularity", "KILOMETERS")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties/$propertyId/unit")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    given()
      .`when`()
      .get("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties/$propertyId/edit")
      .then()
      .statusCode(200)
      .body(containsString("""data-unit-family="DISTANCE""""))
      .body(containsString("""data-unit-storage-granularity="METERS""""))
      .body(containsString("""data-unit-default-granularity="KILOMETERS""""))
  }

  @Test
  fun `setPropertyUnit returns error for property type that does not support units`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Unit Error App ${System.nanoTime()}")
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

    val entityId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "TestEntity")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    val propertyId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Label")
      .formParam("type", "STRING")
      .formParam("nullable", false)
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("propertyId")

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("family", "DISTANCE")
      .formParam("storageGranularity", "METERS")
      .formParam("defaultGranularity", "KILOMETERS")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties/$propertyId/unit")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":false"))
  }

  @Test
  fun `app overview shows active status badge and deactivate button for active app`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Status Badge App ${System.nanoTime()}")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .`when`()
      .get("/ui/developer/apps/$appId")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="app-status-badge""""))
      .body(containsString("""data-testid="deactivate-app-button""""))
  }

  @Test
  fun `deactivate app succeeds and reports it is not blocking`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Deactivate App ${System.nanoTime()}")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/deactivate")
      .then()
      .statusCode(200)
      .contentType(containsString("application/json"))
      .body(containsString("\"ok\":true"))
  }

  @Test
  fun `deactivating an already inactive app returns an error`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Already Inactive App ${System.nanoTime()}")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/deactivate")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/deactivate")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":false"))
  }

  @Test
  fun `activate app succeeds and shows active status again`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Activate App ${System.nanoTime()}")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/deactivate")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    given()
      .`when`()
      .get("/ui/developer/apps/$appId")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="activate-app-button""""))

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/activate")
      .then()
      .statusCode(200)
      .contentType(containsString("application/json"))
      .body(containsString("\"ok\":true"))

    given()
      .`when`()
      .get("/ui/developer/apps/$appId")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="deactivate-app-button""""))
  }

  @Test
  fun `activating an already active app returns an error`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Already Active App ${System.nanoTime()}")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/activate")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":false"))
  }

  @Test
  fun `app overview shows delete button`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Delete Button App ${System.nanoTime()}")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .`when`()
      .get("/ui/developer/apps/$appId")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="delete-app-button""""))
  }

  /**
   * Deleting now only enqueues an outbox event (see ADR 0019) instead of deleting synchronously, so the actual
   * removal - the last step of `AppManagementService.handle` - is observed by polling the dedicated status endpoint
   * instead of asserted immediately after the delete call returns. The outbox worker starts at application startup
   * and is signalled on enqueue, so it picks up the task well within this bound under normal test conditions.
   */
  private fun awaitAppDeleted(appId: String) {
    val deadlineMs = System.currentTimeMillis() + 5000
    while (System.currentTimeMillis() < deadlineMs) {
      val stillExists = given()
        .`when`()
        .get("/ui/developer/apps/$appId/status")
        .then()
        .statusCode(200)
        .extract().body().jsonPath().getBoolean("stillExists")
      if (!stillExists) return
      Thread.sleep(50)
    }
    throw AssertionError("Expected app $appId to be deleted by the outbox dispatcher within 5s")
  }

  @Test
  fun `delete app enqueues its removal and it disappears in the background`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Delete App ${System.nanoTime()}")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/delete")
      .then()
      .statusCode(200)
      .contentType(containsString("application/json"))
      .body(containsString("\"ok\":true"))

    awaitAppDeleted(appId)

    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/developer/apps/$appId")
      .then()
      .statusCode(303)
      .header("Location", containsString("/ui/developer/dashboard"))
  }

  @Test
  fun `delete app is blocked while an active installation exists`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Delete Blocked App ${System.nanoTime()}")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    installedAppRepository.save(
      InstalledApp(
        id = InstalledAppId(UUID.randomUUID().toString()),
        userId = "some-user",
        appId = AppId(appId),
        installedVersionNumber = VersionNumber("1.0.0"),
        installedAt = Instant.now(),
      ),
    )

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/delete")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":false"))

    given()
      .`when`()
      .get("/ui/developer/apps/$appId")
      .then()
      .statusCode(200)
  }

  /** See docs/dev-tests.md ("Test Installations"): mirrors [awaitAppDeleted], but for a single test installation removed via the same outbox event. */
  private fun awaitInstalledAppDeleted(installedAppId: String) {
    val deadlineMs = System.currentTimeMillis() + 5000
    while (System.currentTimeMillis() < deadlineMs) {
      if (installedAppRepository.findById(InstalledAppId(installedAppId)) == null) return
      Thread.sleep(50)
    }
    throw AssertionError("Expected installed app $installedAppId to be deleted by the outbox dispatcher within 5s")
  }

  @Test
  fun `create test installation for a draft version shows up on the app overview page and can be deleted`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Test Installation App ${System.nanoTime()}")
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

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("versionId", versionId)
      .`when`()
      .post("/ui/developer/apps/$appId/test-installations")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    val installedAppId = installedAppRepository.findAllByAppId(AppId(appId)).single().id.value

    given()
      .`when`()
      .get("/ui/developer/apps/$appId")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="test-installation-tile""""))
      .body(containsString("""data-installed-app-id="$installedAppId""""))

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/test-installations/$installedAppId/delete")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    awaitInstalledAppDeleted(installedAppId)

    given()
      .`when`()
      .get("/ui/developer/apps/$appId")
      .then()
      .statusCode(200)
      .body(not(containsString("""data-testid="test-installation-tile"""")))
  }

  @Test
  fun `create test installation fails for unknown version`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Test Installation Unknown Version App ${System.nanoTime()}")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("versionId", "unknown-version")
      .`when`()
      .post("/ui/developer/apps/$appId/test-installations")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":false"))
  }

  @Test
  fun `test installations do not block app deletion`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Test Installation Delete App ${System.nanoTime()}")
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

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("versionId", versionId)
      .`when`()
      .post("/ui/developer/apps/$appId/test-installations")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/delete")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    awaitAppDeleted(appId)
  }

  @Test
  fun `entity editor offers generate test data for installations pinned to the current version`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Generate Test Data UI App ${System.nanoTime()}")
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

    val entityId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "TestEntity")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .`when`()
      .get("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="open-generate-test-data-modal-button""""))
      .body(containsString("""data-testid="generate-test-data-no-installations-hint""""))

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("versionId", versionId)
      .`when`()
      .post("/ui/developer/apps/$appId/test-installations")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    val installedAppId = installedAppRepository.findAllByAppId(AppId(appId)).single().id.value

    given()
      .`when`()
      .get("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="generate-test-data-installation-select""""))
      .body(containsString("""value="$installedAppId""""))
  }

  @Test
  fun `generate test data creates the requested number of objects in the chosen test installation`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Generate Test Data App ${System.nanoTime()}")
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

    val entityId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "TestEntity")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Amount")
      .formParam("type", "LONG")
      .formParam("nullable", false)
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("versionId", versionId)
      .`when`()
      .post("/ui/developer/apps/$appId/test-installations")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    val installedAppId = installedAppRepository.findAllByAppId(AppId(appId)).single().id.value

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("installedAppId", installedAppId)
      .formParam("count", "5")
      .formParam("seed", "42")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/generate-test-data")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    assertThat(appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId(installedAppId), EntityDefinitionId(entityId))).hasSize(5)
  }

  @Test
  fun `generate test data fails for a count outside the allowed range`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Generate Test Data Invalid Count App ${System.nanoTime()}")
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

    val entityId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "TestEntity")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("versionId", versionId)
      .`when`()
      .post("/ui/developer/apps/$appId/test-installations")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    val installedAppId = installedAppRepository.findAllByAppId(AppId(appId)).single().id.value

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("installedAppId", installedAppId)
      .formParam("count", "0")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/generate-test-data")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":false"))
  }

  /** See docs/dev-tests.md ("Execution model"): mirrors [awaitAppDeleted], but for a large generation run enqueued via the outbox. */
  private fun awaitTestDataGenerated(appId: String, versionId: String, entityId: String, installedAppId: String) {
    val deadlineMs = System.currentTimeMillis() + 5000
    while (System.currentTimeMillis() < deadlineMs) {
      val generating = given()
        .queryParam("installedAppId", installedAppId)
        .`when`()
        .get("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/generate-test-data/status")
        .then()
        .statusCode(200)
        .extract().body().jsonPath().getBoolean("generating")
      if (!generating) return
      Thread.sleep(50)
    }
    throw AssertionError("Expected test data generation for entity $entityId to finish by the outbox dispatcher within 5s")
  }

  @Test
  fun `generate test data above the async threshold enqueues the run and it completes in the background`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Generate Test Data Async App ${System.nanoTime()}")
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

    val entityId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "TestEntity")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Amount")
      .formParam("type", "LONG")
      .formParam("nullable", false)
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("versionId", versionId)
      .`when`()
      .post("/ui/developer/apps/$appId/test-installations")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    val installedAppId = installedAppRepository.findAllByAppId(AppId(appId)).single().id.value

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("installedAppId", installedAppId)
      .formParam("count", "101")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/generate-test-data")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))
      .body(containsString("\"queued\":true"))

    awaitTestDataGenerated(appId, versionId, entityId, installedAppId)

    assertThat(appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId(installedAppId), EntityDefinitionId(entityId))).hasSize(101)
  }

  @Test
  fun `dashboard shows inactive badge for inactive app`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Inactive Badge App ${System.nanoTime()}")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/deactivate")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    given()
      .`when`()
      .get("/ui/developer/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="app-inactive-badge""""))
  }

  @Test
  fun `app overview disables new version tile for inactive app`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Inactive New Version App ${System.nanoTime()}")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/deactivate")
      .then()
      .statusCode(200)

    given()
      .`when`()
      .get("/ui/developer/apps/$appId")
      .then()
      .statusCode(200)
      .body(containsString("app-editor-disabled"))
      .body(containsString("""aria-disabled="true""""))
      .body(containsString("""data-testid="app-inactive-notice""""))
  }

  @Test
  fun `creating a draft version fails for inactive app`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Inactive Create Version App ${System.nanoTime()}")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/deactivate")
      .then()
      .statusCode(200)

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/versions")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":false"))
  }

  @Test
  fun `deleting and publishing a draft version fail once the app is deactivated`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Inactive Draft Actions App ${System.nanoTime()}")
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

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/deactivate")
      .then()
      .statusCode(200)

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("bumpType", "FEATURE")
      .formParam("releaseNotes", "Notes")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/publish")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":false"))

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/delete")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":false"))
  }

  @Test
  fun `updating an app fails once it is deactivated`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Inactive Update App ${System.nanoTime()}")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/deactivate")
      .then()
      .statusCode(200)

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "New Name")
      .`when`()
      .post("/ui/developer/apps/$appId")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":false"))
  }

  @Test
  fun `app overview disables edit app button for inactive app`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Inactive Edit App ${System.nanoTime()}")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/deactivate")
      .then()
      .statusCode(200)

    given()
      .`when`()
      .get("/ui/developer/apps/$appId")
      .then()
      .statusCode(200)
      .body(containsString("""id="editAppButton""""))
      .body(containsString("app-editor-disabled"))
  }

  @Test
  fun `version editor shows inactive notice for a draft version once the app is deactivated`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Inactive Version Editor App ${System.nanoTime()}")
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

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/deactivate")
      .then()
      .statusCode(200)

    given()
      .`when`()
      .get("/ui/developer/apps/$appId/versions/$versionId")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="app-inactive-notice""""))
      .body(containsString("app-editor-disabled"))
  }

  @Test
  fun `delete app modal shows current installation count up front`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Delete Modal Count App ${System.nanoTime()}")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    installedAppRepository.save(
      InstalledApp(
        id = InstalledAppId(UUID.randomUUID().toString()),
        userId = "some-user",
        appId = AppId(appId),
        installedVersionNumber = VersionNumber("1.0.0"),
        installedAt = Instant.now(),
      ),
    )

    given()
      .`when`()
      .get("/ui/developer/apps/$appId")
      .then()
      .statusCode(200)
      .body(containsString("Aktuell bestehen 1 aktive Installation(en)"))
  }

  @Test
  fun `nested OBJECT property properties can be added and shown when descending via path`() {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Nested Properties App ${System.nanoTime()}")
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

    val entityId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "TestEntity")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    val propertyId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Address")
      .formParam("type", "OBJECT")
      .formParam("nullable", false)
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("propertyId")

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Street")
      .formParam("type", "STRING")
      .formParam("nullable", false)
      .formParam("path", propertyId)
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    val nestedPageBody = given()
      .queryParam("path", propertyId)
      .`when`()
      .get("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertThat(nestedPageBody).contains("Street")
    assertThat(nestedPageBody).doesNotContain("Computed Properties")
    assertThat(nestedPageBody).doesNotContain("Sort Order")
  }
}
