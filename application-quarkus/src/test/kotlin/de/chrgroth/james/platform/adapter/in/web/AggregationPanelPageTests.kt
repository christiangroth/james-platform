package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.app.AggregationDefinition
import de.chrgroth.james.platform.domain.model.app.AggregationDefinitionId
import de.chrgroth.james.platform.domain.model.app.AggregationFunction
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.TimeBucket
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

  private data class RunShoeSetup(
    val appId: String,
    val versionId: String,
    val runEntityId: String,
    val shoeEntityId: String,
    val shoeNamePropertyId: String,
    val distancePropertyId: String,
    val categoryPropertyId: String,
    val shoeRefPropertyId: String,
  )

  /**
   * Creates a draft app with a "Shoe" entity (Name STRING, displayText "{Name}") and a "Run" entity (Distance LONG,
   * Category STRING, ShoeRef REF -> Shoe), both unpublished so a test can add its own grouped/time-bucketed
   * aggregation(s) to Run before publishing/installing via [publishAndInstall].
   */
  private fun createRunShoeApp(appName: String): RunShoeSetup {
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

    val shoeEntityId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Shoe")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    val shoeNamePropertyId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Name")
      .formParam("type", "STRING")
      .formParam("nullable", "false")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$shoeEntityId/properties")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("propertyId")

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("displayText", "{Name}")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$shoeEntityId/display-text")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    val runEntityId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Run")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    val distancePropertyId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Distance")
      .formParam("type", "LONG")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$runEntityId/properties")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("propertyId")

    val categoryPropertyId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Category")
      .formParam("type", "STRING")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$runEntityId/properties")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("propertyId")

    val shoeRefPropertyId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "ShoeRef")
      .formParam("type", "REF")
      .formParam("targetEntityId", shoeEntityId)
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$runEntityId/properties")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("propertyId")

    return RunShoeSetup(appId, versionId, runEntityId, shoeEntityId, shoeNamePropertyId, distancePropertyId, categoryPropertyId, shoeRefPropertyId)
  }

  /** Adds [aggregations] to [setup]'s Run entity, publishes the draft, installs it for the test user, and returns the installedAppId. */
  private fun publishAndInstall(setup: RunShoeSetup, appName: String, aggregations: List<AggregationDefinition>): String {
    val version = appVersionRepository.findById(AppVersionId(setup.versionId))!!
    val updatedEntityDefinitions = version.entityDefinitions.map { entity ->
      if (entity.id.value != setup.runEntityId) entity else entity.copy(aggregations = entity.aggregations + aggregations)
    }
    appVersionRepository.save(version.copy(entityDefinitions = updatedEntityDefinitions))

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("bumpType", "BUGFIX")
      .formParam("releaseNotes", "Initial release")
      .`when`()
      .post("/ui/developer/apps/${setup.appId}/versions/publish")
      .then()
      .statusCode(200)

    given()
      .`when`()
      .post("/ui/user/app-store/apps/${setup.appId}/install")
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

  @Test
  fun `entity detail page shows one table row per group for a groupBy aggregation, sorted by label`() {
    val appName = "Grouped Aggregation App ${System.nanoTime()}"
    val setup = createRunShoeApp(appName)
    val aggregationId = AggregationDefinitionId(UUID.randomUUID().toString())
    val aggregation = AggregationDefinition(
      id = aggregationId,
      name = "Kilometer je Kategorie",
      function = AggregationFunction.SUM,
      sourceProperty = PropertyId(setup.distancePropertyId),
      groupBy = PropertyId(setup.categoryPropertyId),
    )
    val installedAppId = publishAndInstall(setup, appName, listOf(aggregation))

    aggregationRepository.save(
      AggregationValue(
        id = AggregationValueId(InstalledAppId(installedAppId), aggregationId, groupKey = "Trail"),
        value = 12.0,
        status = AggregationValueStatus.UP_TO_DATE,
        updatedAt = Instant.now(),
        sampleCount = 2,
      ),
    )
    aggregationRepository.save(
      AggregationValue(
        id = AggregationValueId(InstalledAppId(installedAppId), aggregationId, groupKey = "Strasse"),
        value = 30.0,
        status = AggregationValueStatus.UP_TO_DATE,
        updatedAt = Instant.now(),
        sampleCount = 3,
      ),
    )

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/entities/${setup.runEntityId}")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"aggregation-table\""), "Expected the aggregation table to be rendered")
    assertTrue(html.contains("Kilometer je Kategorie"), "Expected the aggregation name to be rendered")
    assertTrue(html.contains("Strasse") && html.contains("Trail"), "Expected both group labels to be rendered")
    val strasseIndex = html.indexOf("Strasse")
    val trailIndex = html.indexOf("Trail")
    assertTrue(strasseIndex in 0 until trailIndex, "Expected groups sorted alphabetically by label (Strasse before Trail)")
  }

  @Test
  fun `entity detail page shows one table row per period for a time-bucketed aggregation, sorted chronologically descending`() {
    val appName = "Bucketed Aggregation App ${System.nanoTime()}"
    val setup = createRunShoeApp(appName)
    val aggregationId = AggregationDefinitionId(UUID.randomUUID().toString())
    val aggregation = AggregationDefinition(
      id = aggregationId,
      name = "Kilometer je Monat",
      function = AggregationFunction.SUM,
      sourceProperty = PropertyId(setup.distancePropertyId),
      timeBucket = TimeBucket.MONAT,
    )
    val installedAppId = publishAndInstall(setup, appName, listOf(aggregation))

    aggregationRepository.save(
      AggregationValue(
        id = AggregationValueId(InstalledAppId(installedAppId), aggregationId, bucketKey = "2026-01"),
        value = 10.0,
        status = AggregationValueStatus.UP_TO_DATE,
        updatedAt = Instant.now(),
        sampleCount = 1,
      ),
    )
    aggregationRepository.save(
      AggregationValue(
        id = AggregationValueId(InstalledAppId(installedAppId), aggregationId, bucketKey = "2026-03"),
        value = 20.0,
        status = AggregationValueStatus.UP_TO_DATE,
        updatedAt = Instant.now(),
        sampleCount = 1,
      ),
    )

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/entities/${setup.runEntityId}")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("03/2026") && html.contains("01/2026"), "Expected both period labels to be rendered")
    val marchIndex = html.indexOf("03/2026")
    val januaryIndex = html.indexOf("01/2026")
    assertTrue(marchIndex in 0 until januaryIndex, "Expected periods sorted chronologically descending (03/2026 before 01/2026)")
  }

  @Test
  fun `entity detail page shows period and group columns for a combined refPath and timeBucket aggregation, resolving the referenced entity's display text`() {
    val appName = "Combined Aggregation App ${System.nanoTime()}"
    val setup = createRunShoeApp(appName)
    val aggregationId = AggregationDefinitionId(UUID.randomUUID().toString())
    val aggregation = AggregationDefinition(
      id = aggregationId,
      name = "Kilometer je Schuh und Monat",
      function = AggregationFunction.SUM,
      sourceProperty = PropertyId(setup.distancePropertyId),
      refPath = PropertyId(setup.shoeRefPropertyId),
      timeBucket = TimeBucket.MONAT,
    )
    val installedAppId = publishAndInstall(setup, appName, listOf(aggregation))

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("entityTypeId", setup.shoeEntityId)
      .formParam("prop_${setup.shoeNamePropertyId}", "Asics")
      .`when`()
      .post("/ui/user/apps/$installedAppId/data")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    val shoeListHtml = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/entities/${setup.shoeEntityId}")
      .then()
      .statusCode(200)
      .extract().body().asString()
    val shoeDataId = Regex("""data-href="/ui/user/apps/$installedAppId/data/([^"]+)"""").find(shoeListHtml)?.groupValues?.get(1) ?: ""
    assertTrue(shoeDataId.isNotBlank(), "Expected to find the created Shoe's data id")

    aggregationRepository.save(
      AggregationValue(
        id = AggregationValueId(InstalledAppId(installedAppId), aggregationId, groupKey = shoeDataId, bucketKey = "2026-02"),
        value = 42.0,
        status = AggregationValueStatus.UP_TO_DATE,
        updatedAt = Instant.now(),
        sampleCount = 1,
      ),
    )

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/entities/${setup.runEntityId}")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("02/2026"), "Expected the period column to show the formatted month label")
    assertTrue(html.contains("Asics"), "Expected the group column to show the referenced Shoe's display text")
    assertTrue(html.contains("42"), "Expected the formatted aggregation value to be rendered")
  }

  @Test
  fun `entity detail page shows the update-pending hint for a stale row in a grouped aggregation table`() {
    val appName = "Stale Grouped Aggregation App ${System.nanoTime()}"
    val setup = createRunShoeApp(appName)
    val aggregationId = AggregationDefinitionId(UUID.randomUUID().toString())
    val aggregation = AggregationDefinition(
      id = aggregationId,
      name = "Kilometer je Kategorie",
      function = AggregationFunction.SUM,
      sourceProperty = PropertyId(setup.distancePropertyId),
      groupBy = PropertyId(setup.categoryPropertyId),
    )
    val installedAppId = publishAndInstall(setup, appName, listOf(aggregation))

    aggregationRepository.save(
      AggregationValue(
        id = AggregationValueId(InstalledAppId(installedAppId), aggregationId, groupKey = "Trail"),
        value = 12.0,
        status = AggregationValueStatus.STALE,
        updatedAt = Instant.now(),
        sampleCount = 2,
      ),
    )

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/entities/${setup.runEntityId}")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"aggregation-table-stale-badge\""), "Expected the table-level stale hint")
    assertTrue(html.contains("data-testid=\"aggregation-table-row-stale-badge\""), "Expected the row-level stale hint")
  }

  @Test
  fun `entity detail page does not render a table for a grouped aggregation with no computed values yet`() {
    val appName = "Empty Grouped Aggregation App ${System.nanoTime()}"
    val setup = createRunShoeApp(appName)
    val aggregation = AggregationDefinition(
      id = AggregationDefinitionId(UUID.randomUUID().toString()),
      name = "Kilometer je Kategorie",
      function = AggregationFunction.SUM,
      sourceProperty = PropertyId(setup.distancePropertyId),
      groupBy = PropertyId(setup.categoryPropertyId),
    )
    val installedAppId = publishAndInstall(setup, appName, listOf(aggregation))

    val html = given()
      .`when`()
      .get("/ui/user/apps/$installedAppId/entities/${setup.runEntityId}")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(!html.contains("data-testid=\"aggregation-table\""), "Expected no table for an aggregation with no computed values yet")
    assertTrue(!html.contains("data-testid=\"aggregation-panel\""), "Expected no aggregation panel at all since this entity has no other aggregations")
  }
}
