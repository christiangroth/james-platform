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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@QuarkusTest
@TestSecurity(user = "test-import-user", roles = ["DEVELOPER", "DATA_IMPORT"])
class UserAppImportsPageTests {

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @BeforeEach
  fun setup() {
    ensureUser("test-import-user", setOf(UserRole.DEVELOPER, UserRole.DATA_IMPORT))
    ensureUser("test-no-import-user", setOf(UserRole.DEVELOPER))
  }

  private fun ensureUser(username: String, roles: Set<UserRole>) {
    if (userRepository.findByUsername(Username(username)) == null) {
      userRepository.save(
        User(
          id = UserId(UUID.randomUUID().toString()),
          username = Username(username),
          passwordHash = "test-hash",
          roles = roles,
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

    val dashboardHtml = given()
      .`when`()
      .get("/ui/user/dashboard")
      .then()
      .statusCode(200)
      .extract().body().asString()

    return Regex("""href="/ui/user/apps/([^"]+)"[^>]*aria-label="App ${Regex.escape(appName)} öffnen"""")
      .find(dashboardHtml)?.groupValues?.get(1) ?: ""
  }

  @Test
  fun `app detail page shows import button for users with DATA_IMPORT role`() {
    val appName = "Import Button App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    addEntity(appId, versionId, "Entity One")
    val installedAppId = publishAndInstall(appId, appName)

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"import-button\""), "Expected the import button to be rendered for a DATA_IMPORT user")
    assertTrue(html.contains("/ui/user/apps/$installedAppId/imports"), "Expected the import button to link to the imports page")
  }

  @Test
  @TestSecurity(user = "test-no-import-user", roles = ["DEVELOPER"])
  fun `app detail page hides import button for users without DATA_IMPORT role`() {
    val appName = "No Import Button App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    addEntity(appId, versionId, "Entity One")
    val installedAppId = publishAndInstall(appId, appName)

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertFalse(html.contains("data-testid=\"import-button\""), "Expected no import button for a user without the DATA_IMPORT role")
  }

  @Test
  fun `imports page renders empty placeholder for an installed app`() {
    val appName = "Imports Page App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    addEntity(appId, versionId, "Entity One")
    val installedAppId = publishAndInstall(appId, appName)

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/imports")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"no-imports-message\""), "Expected the empty-state message on the imports page")
  }

  @Test
  @TestSecurity(user = "test-no-import-user", roles = ["DEVELOPER"])
  fun `imports page is forbidden for users without DATA_IMPORT role`() {
    val appName = "Forbidden Imports App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    addEntity(appId, versionId, "Entity One")
    val installedAppId = publishAndInstall(appId, appName)

    given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/imports")
      .then()
      .statusCode(403)
  }
}
