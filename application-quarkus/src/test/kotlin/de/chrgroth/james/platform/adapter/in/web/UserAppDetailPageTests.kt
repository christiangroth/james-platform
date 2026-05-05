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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@QuarkusTest
@TestSecurity(user = "test-tab-user", roles = ["DEVELOPER"])
class UserAppDetailTabTests {

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @BeforeEach
  fun setup() {
    if (userRepository.findByUsername(Username("test-tab-user")) == null) {
      userRepository.save(
        User(
          id = UserId(UUID.randomUUID().toString()),
          username = Username("test-tab-user"),
          passwordHash = "test-hash",
          roles = setOf(UserRole.DEVELOPER),
          createdAt = Instant.now(),
        ),
      )
    }
  }

  private fun setupMultiEntityApp(appName: String): Triple<String, String, String> {
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

    val entity1Id = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Entity One")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    val entity2Id = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Entity Two")
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

    return Triple(installedAppId, entity1Id, entity2Id)
  }

  private fun assertTabPaneActive(html: String, entityId: String, expectedActive: Boolean) {
    val activePattern = Regex("""class="tab-pane fade show active"\s+id="tab-${Regex.escape(entityId)}"""")
    if (expectedActive) {
      assertTrue(activePattern.containsMatchIn(html), "Expected tab pane for entity $entityId to be active")
    } else {
      assertFalse(activePattern.containsMatchIn(html), "Expected tab pane for entity $entityId to NOT be active")
    }
  }

  @Test
  fun `app detail page without tab param shows first entity tab as active`() {
    val (installedAppId, entity1Id, _) = setupMultiEntityApp("Tab Default App ${System.nanoTime()}")

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTabPaneActive(html, entity1Id, expectedActive = true)
  }

  @Test
  fun `app detail page with tab param for second entity shows second entity tab as active`() {
    val (installedAppId, entity1Id, entity2Id) = setupMultiEntityApp("Tab Second App ${System.nanoTime()}")

    val html = given()
      .queryParam("tab", entity2Id)
      .`when`()
      .get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTabPaneActive(html, entity2Id, expectedActive = true)
    assertTabPaneActive(html, entity1Id, expectedActive = false)
  }

  @Test
  fun `app detail page with unknown tab param falls back to first entity tab as active`() {
    val (installedAppId, entity1Id, _) = setupMultiEntityApp("Tab Fallback App ${System.nanoTime()}")

    val html = given()
      .queryParam("tab", "unknown-entity-id")
      .`when`()
      .get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTabPaneActive(html, entity1Id, expectedActive = true)
  }
}
