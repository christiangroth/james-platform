package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.app.PredefinedSmartDefault
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

@QuarkusTest
@TestSecurity(user = "test-smart-default-user", roles = ["DEVELOPER"])
class SmartDefaultPageTests {

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @BeforeEach
  fun setup() {
    if (userRepository.findByUsername(Username("test-smart-default-user")) == null) {
      userRepository.save(
        User(
          id = UserId(UUID.randomUUID().toString()),
          username = Username("test-smart-default-user"),
          passwordHash = "test-hash",
          roles = setOf(UserRole.DEVELOPER),
          createdAt = Instant.now(),
        ),
      )
    }
  }

  private fun setupAppWithSmartDefault(appName: String, smartDefault: PredefinedSmartDefault): Pair<String, String> {
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
      .formParam("name", "OrderDate")
      .formParam("type", smartDefault.types.first().name)
      .formParam("nullable", true)
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

    val propertyId = Regex("""data-property-id="([^"]+)"""").find(entityHtml)?.groupValues?.get(1) ?: ""

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("smartDefault", smartDefault.script)
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties/$propertyId/smart-default")
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

    return Pair(installedAppId, entityId)
  }

  @Test
  fun `new data form pre-fills DATE property with DATE_TODAY smart default`() {
    val appName = "Smart Default Date App ${System.nanoTime()}"
    val (installedAppId, entityId) = setupAppWithSmartDefault(appName, PredefinedSmartDefault.DATE_TODAY)
    val expectedDate = LocalDate.now(ZoneOffset.UTC).toString()

    given()
      .queryParam("entityId", entityId)
      .`when`()
      .get("/ui/user/apps/$installedAppId/data/new")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""value="$expectedDate""""))
  }

  @Test
  fun `new data form pre-fills TIME property with TIME_NOW_CURRENT_MINUTE smart default`() {
    val appName = "Smart Default Time App ${System.nanoTime()}"
    val (installedAppId, entityId) = setupAppWithSmartDefault(appName, PredefinedSmartDefault.TIME_NOW_CURRENT_MINUTE)
    val expectedHourPrefix = LocalTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS).toString().substringBefore(":")

    given()
      .queryParam("entityId", entityId)
      .`when`()
      .get("/ui/user/apps/$installedAppId/data/new")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""value="$expectedHourPrefix:"""))
  }

  @Test
  fun `new data form pre-fills DATETIME property with DATETIME_NOW_CURRENT_MINUTE smart default`() {
    val appName = "Smart Default DateTime App ${System.nanoTime()}"
    val (installedAppId, entityId) = setupAppWithSmartDefault(appName, PredefinedSmartDefault.DATETIME_NOW_CURRENT_MINUTE)
    val expectedDatePrefix = LocalDateTime.now(ZoneOffset.UTC).toLocalDate().toString()

    given()
      .queryParam("entityId", entityId)
      .`when`()
      .get("/ui/user/apps/$installedAppId/data/new")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""value="$expectedDatePrefix"""))
  }
}
