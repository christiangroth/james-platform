package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

@QuarkusTest
@TestSecurity(user = "test-monitoring", roles = ["MONITORING"])
class LogsPageTests {

  @Inject
  private lateinit var logBuffer: UiLogBuffer

  @AfterEach
  fun clearLogBuffer() {
    logBuffer.clear()
  }

  @Test
  fun `logs page is available and displays logs heading`() {
    given()
      .`when`()
      .get("/logs")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Logs"))
  }

  @Test
  fun `logs page contains logs ui link in navbar`() {
    given()
      .`when`()
      .get("/logs")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="logs-ui-link""""))
  }

  @Test
  fun `logs page displays newest entries first with level badges and expandable stacktrace`() {
    val now = Instant.now()
    logBuffer.add(
      UiLogEntry(
        timestamp = now.minusSeconds(120),
        level = "WARN",
        clazz = "de.chrgroth.OldClass",
        message = "older warning",
        stacktrace = null,
      ),
    )
    logBuffer.add(
      UiLogEntry(
        timestamp = now.minusSeconds(30),
        level = "ERROR",
        clazz = "de.chrgroth.NewClass",
        message = "newer error",
        stacktrace = "java.lang.RuntimeException: boom",
      ),
    )

    val body = given()
      .`when`()
      .get("/logs")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="logs-table""""))
      .body(containsString("older warning"))
      .body(containsString("newer error"))
      .body(containsString("app-badge-warn"))
      .body(containsString("app-badge-failed"))
      .body(containsString("""data-testid="log-stacktrace""""))
      .extract()
      .asString()

    assertTrue(body.indexOf("newer error") < body.indexOf("older warning"))
  }
}
