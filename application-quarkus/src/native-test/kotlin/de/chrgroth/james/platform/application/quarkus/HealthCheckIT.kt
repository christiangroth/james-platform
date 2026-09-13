package de.chrgroth.james.platform.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

// Renders the real /health page against the packaged native executable. HealthStats is the largest untyped
// Template.data() object graph in the app (MongoCollectionStats/MongoQueryStats/CronjobStats/ScriptExecutionStats/
// ImportCleanupStats/ConfigurationStats), and every list on it is naturally non-empty as soon as the app has booted
// (real Mongo collections, real registered cronjobs, real config) - no test data seeding needed. Also exercises the
// job.nextExecution.toEpochMilliseconds() (kotlin.time.Instant) template call fixed in TemplateFormattingExtensions.
// See #672.
@QuarkusIntegrationTest
class HealthCheckIT {

  @Test
  fun `health page renders successfully with populated cronjob and mongodb stats`() {
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, SessionCookieForge.forgeSessionCookie("admin"))
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""id="health-section""""))
      .body(containsString("""data-testid="cronjobs-table""""))
  }

  @Test
  fun `health snippet endpoints render successfully`() {
    listOf(
      "/health/snippets/cronjobs",
      "/health/snippets/mongodb-collections",
      "/health/snippets/mongodb-queries",
      "/health/snippets/http-responses",
      "/health/snippets/scripting",
      "/health/snippets/import-cleanup",
    ).forEach { path ->
      given()
        .cookie(SessionCookieForge.COOKIE_NAME, SessionCookieForge.forgeSessionCookie("admin"))
        .`when`()
        .get(path)
        .then()
        .statusCode(200)
        .contentType(containsString("text/html"))
    }
  }
}
