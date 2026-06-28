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
      .body(containsString("&lt;STRING&gt;"))
  }

  @Test
  fun `object property nested properties are embedded as array for define structure button`() {
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
      .contentType("application/json")
      .body("""[{"name":"Street","type":"STRING","nullable":false}]""")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties/$propertyId/nested-properties")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    val pageBody = given()
      .`when`()
      .get("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    val nestedPropertiesJson = pageBody
      .substringAfter("id=\"nested-properties-data\">")
      .substringBefore("</script>")
    assertThat(nestedPropertiesJson).contains("\"name\":\"Street\"")
    assertThat(nestedPropertiesJson).doesNotContain("\"name\":\"Address\"")
  }
}

@QuarkusTest
class DeveloperAppPageUnauthenticatedTests {

  @Test
  fun `unauthenticated access to developer dashboard redirects to login`() {
    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/developer/dashboard")
      .then()
      .statusCode(307)
  }

  @Test
  fun `unauthenticated access to app overview redirects to login`() {
    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/developer/apps/some-id")
      .then()
      .statusCode(307)
  }
}
