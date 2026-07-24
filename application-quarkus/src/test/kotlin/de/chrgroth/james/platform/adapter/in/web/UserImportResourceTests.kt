package de.chrgroth.james.platform.adapter.`in`.web

import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.out.imports.ImportFetchPort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Instant
import java.util.UUID

@QuarkusTest
@TestSecurity(user = "test-import-trigger-user", roles = ["DEVELOPER", "DATA_IMPORT"])
class UserImportResourceTests {

  @InjectMock
  lateinit var importFetch: ImportFetchPort

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @BeforeEach
  fun setup() {
    if (userRepository.findByUsername(Username("test-import-trigger-user")) == null) {
      userRepository.save(
        User(
          id = UserId(UUID.randomUUID().toString()),
          username = Username("test-import-trigger-user"),
          passwordHash = "test-hash",
          roles = setOf(UserRole.DEVELOPER, UserRole.DATA_IMPORT),
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

  private fun installApp(): String {
    val appName = "Import Resource App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    addEntity(appId, versionId, "Entity One")
    return publishAndInstall(appId, appName)
  }

  @Test
  fun `trigger import creates a downloaded document and appears in the table`() {
    val installedAppId = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"foo":"bar"}""".right())

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("sourceUrl", "https://example.com/data")
      .formParam("bearerToken", "secret-token")
      .`when`()
      .post("/ui/user/apps/$installedAppId/imports")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val tableHtml = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/imports/table")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(tableHtml.contains("data-testid=\"imports-table\""), "Expected the imports table to be rendered")
    assertTrue(tableHtml.contains("data-testid=\"import-status\""), "Expected a status cell for the created import document")
  }

  @Test
  fun `trigger import reports an error when the response is not a JSON object`() {
    val installedAppId = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("[1,2,3]".right())

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("sourceUrl", "https://example.com/data")
      .formParam("bearerToken", "secret-token")
      .`when`()
      .post("/ui/user/apps/$installedAppId/imports")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `trigger import reports an error when the source url is blank`() {
    val installedAppId = installApp()

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("sourceUrl", "")
      .formParam("bearerToken", "secret-token")
      .`when`()
      .post("/ui/user/apps/$installedAppId/imports")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `trigger import reports an error when the fetch fails`() {
    val installedAppId = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn(ImportError.FETCH_FAILED.left())

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("sourceUrl", "https://example.com/data")
      .formParam("bearerToken", "secret-token")
      .`when`()
      .post("/ui/user/apps/$installedAppId/imports")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `delete import document removes it from the table`() {
    val installedAppId = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"foo":"bar"}""".right())

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("sourceUrl", "https://example.com/data")
      .formParam("bearerToken", "secret-token")
      .`when`()
      .post("/ui/user/apps/$installedAppId/imports")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val tableHtml = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/imports/table")
      .then()
      .statusCode(200)
      .extract().body().asString()
    val importId = Regex("""data-import-id="([^"]+)"""").find(tableHtml)?.groupValues?.get(1)
      ?: error("Expected an import id in the rendered table")

    given()
      .`when`()
      .post("/ui/user/apps/$installedAppId/imports/$importId/delete")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val afterDeleteHtml = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/imports/table")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(afterDeleteHtml.contains("data-testid=\"no-imports-message\""), "Expected the empty-state message after deleting the only import document")
  }

  @Test
  fun `delete import document reports an error for an unknown document id`() {
    val installedAppId = installApp()

    given()
      .`when`()
      .post("/ui/user/apps/$installedAppId/imports/unknown-id/delete")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }
}
