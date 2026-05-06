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
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@QuarkusTest
@TestSecurity(user = "test-computed-prop-user", roles = ["DEVELOPER"])
class ComputedPropertyPanelTests {

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @BeforeEach
  fun setup() {
    if (userRepository.findByUsername(Username("test-computed-prop-user")) == null) {
      userRepository.save(
        User(
          id = UserId(UUID.randomUUID().toString()),
          username = Username("test-computed-prop-user"),
          passwordHash = "test-hash",
          roles = setOf(UserRole.DEVELOPER),
          createdAt = Instant.now(),
        ),
      )
    }
  }

  private fun setupAppWithComputedProperty(appName: String): Triple<String, String, String> {
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

    val entityId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Order")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "DoublePrice")
      .formParam("type", "LONG")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/computed-properties")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    val entityHtml = given()
      .`when`()
      .get("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    val computedPropertyId = Regex("""data-computed-property-id="([^"]+)"""").find(entityHtml)?.groupValues?.get(1) ?: ""

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("script", "42")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/computed-properties/$computedPropertyId/script")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

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

    val installedAppId = Regex("""href="/ui/user/apps/([^"]+)"[^>]*aria-label="Open app ${Regex.escape(appName)}"""")
      .find(dashboardHtml)?.groupValues?.get(1) ?: ""

    return Triple(installedAppId, entityId, computedPropertyId)
  }

  @Test
  fun `edit data page shows computed properties accordion when entity has computed properties`() {
    val appName = "Computed Panel App ${System.nanoTime()}"
    val (installedAppId, entityId, _) = setupAppWithComputedProperty(appName)

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("entityTypeId", entityId)
      .`when`()
      .post("/ui/user/apps/$installedAppId/data")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    val appDetailHtml = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    val dataId = Regex("""data-href="/ui/user/apps/$installedAppId/data/([^"]+)"""")
      .find(appDetailHtml)?.groupValues?.get(1) ?: ""

    given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/data/$dataId")
      .then()
      .statusCode(200)
      .body(containsString("computed-properties-toggle"))
      .body(containsString("Computed Properties"))
      .body(containsString("DoublePrice"))
  }

  @Test
  fun `edit data page does not show computed properties accordion when entity has no computed properties`() {
    val appName = "No Computed Panel App ${System.nanoTime()}"

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

    val entityId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Item")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

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

    val installedAppId = Regex("""href="/ui/user/apps/([^"]+)"[^>]*aria-label="Open app ${Regex.escape(appName)}"""")
      .find(dashboardHtml)?.groupValues?.get(1) ?: ""

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("entityTypeId", entityId)
      .`when`()
      .post("/ui/user/apps/$installedAppId/data")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    val appDetailHtml = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    val dataId = Regex("""data-href="/ui/user/apps/$installedAppId/data/([^"]+)"""")
      .find(appDetailHtml)?.groupValues?.get(1) ?: ""

    given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/data/$dataId")
      .then()
      .statusCode(200)
      .body(not(containsString("computed-properties-toggle")))
      .body(not(containsString("Computed Properties")))
  }
}
