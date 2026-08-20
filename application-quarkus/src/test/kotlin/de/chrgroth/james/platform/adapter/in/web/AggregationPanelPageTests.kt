package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.app.AggregationDefinition
import de.chrgroth.james.platform.domain.model.app.AggregationDefinitionId
import de.chrgroth.james.platform.domain.model.app.AggregationFunction
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValue
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValueId
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValueStatus
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.readmodel.AggregationRepositoryPort
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
@TestSecurity(user = "test-aggregation-panel-user", roles = ["DEVELOPER"])
class AggregationPanelPageTests {

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @Inject
  lateinit var appVersionRepository: AppVersionRepositoryPort

  @Inject
  lateinit var aggregationRepository: AggregationRepositoryPort

  @BeforeEach
  fun setup() {
    if (userRepository.findByUsername(Username("test-aggregation-panel-user")) == null) {
      userRepository.save(
        User(
          id = UserId(UUID.randomUUID().toString()),
          username = Username("test-aggregation-panel-user"),
          passwordHash = "test-hash",
          roles = setOf(UserRole.DEVELOPER),
          createdAt = Instant.now(),
        ),
      )
    }
  }

  /** Sets up an app with one Entity carrying an ungrouped, non-time-bucketed aggregation, installs it, and returns (installedAppId, entityId, aggregationDefinitionId). */
  private fun setupAppWithAggregation(appName: String, aggregationName: String): Triple<String, String, AggregationDefinitionId> {
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

    val propertyId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Amount")
      .formParam("type", "LONG")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("propertyId")

    val aggregationId = AggregationDefinitionId(UUID.randomUUID().toString())
    val version = appVersionRepository.findById(AppVersionId(versionId))!!
    val updatedEntityDefinitions = version.entityDefinitions.map { entity ->
      if (entity.id.value != entityId) {
        entity
      } else {
        entity.copy(
          aggregations = entity.aggregations + AggregationDefinition(
            id = aggregationId,
            name = aggregationName,
            function = AggregationFunction.SUM,
            sourceProperty = PropertyId(propertyId),
          ),
        )
      }
    }
    appVersionRepository.save(version.copy(entityDefinitions = updatedEntityDefinitions))

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

    val installedAppId = Regex("""href="/ui/user/apps/([^"]+)"[^>]*aria-label="App ${Regex.escape(appName)} öffnen"""")
      .find(dashboardHtml)?.groupValues?.get(1) ?: ""

    return Triple(installedAppId, entityId, aggregationId)
  }

  @Test
  fun `entity detail page shows the aggregation label and formatted value for an up-to-date aggregation`() {
    val appName = "Aggregation Panel App ${System.nanoTime()}"
    val (installedAppId, entityId, aggregationId) = setupAppWithAggregation(appName, "Gesamtsumme")

    aggregationRepository.save(
      AggregationValue(
        id = AggregationValueId(InstalledAppId(installedAppId), aggregationId),
        value = 1234.5,
        status = AggregationValueStatus.UP_TO_DATE,
        updatedAt = Instant.now(),
        sampleCount = 3,
      ),
    )

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/entities/$entityId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"aggregation-panel\""), "Expected the aggregation panel to be rendered")
    assertTrue(html.contains("Gesamtsumme"), "Expected the aggregation label to be rendered")
    assertTrue(html.contains("1.234,5"), "Expected the German-formatted aggregation value to be rendered")
    assertTrue(!html.contains("data-testid=\"aggregation-stale-badge\""), "Expected no stale hint for an up-to-date aggregation")
  }

  @Test
  fun `entity detail page shows an update-pending hint for a stale aggregation`() {
    val appName = "Stale Aggregation App ${System.nanoTime()}"
    val (installedAppId, entityId, aggregationId) = setupAppWithAggregation(appName, "Anzahl")

    aggregationRepository.save(
      AggregationValue(
        id = AggregationValueId(InstalledAppId(installedAppId), aggregationId),
        value = 42.0,
        status = AggregationValueStatus.STALE,
        updatedAt = Instant.now(),
        sampleCount = 1,
      ),
    )

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/entities/$entityId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"aggregation-stale-badge\""), "Expected the update-pending hint for a stale aggregation")
  }

  @Test
  fun `entity detail page does not show the aggregation panel when the entity has no aggregations`() {
    val appName = "No Aggregation App ${System.nanoTime()}"
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

    val installedAppId = Regex("""href="/ui/user/apps/([^"]+)"[^>]*aria-label="App ${Regex.escape(appName)} öffnen"""")
      .find(dashboardHtml)?.groupValues?.get(1) ?: ""

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/entities/$entityId")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(!html.contains("data-testid=\"aggregation-panel\""), "Expected no aggregation panel for an entity without aggregations")
  }
}
