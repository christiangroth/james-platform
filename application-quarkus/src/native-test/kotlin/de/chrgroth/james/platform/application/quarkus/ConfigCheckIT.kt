package de.chrgroth.james.platform.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

// Renders the real /config page against the packaged native executable. ConfigurationStats/ConfigEntry are always
// populated with real application config and environment entries, so this reliably exercises their Qute bindings
// with non-empty lists. See #672.
@QuarkusIntegrationTest
class ConfigCheckIT {

  @Test
  fun `config page renders successfully with populated config and env tables`() {
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, SessionCookieForge.forgeSessionCookie("admin"))
      .`when`()
      .get("/config")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="config-table""""))
      .body(containsString("""data-testid="env-table""""))
  }
}
