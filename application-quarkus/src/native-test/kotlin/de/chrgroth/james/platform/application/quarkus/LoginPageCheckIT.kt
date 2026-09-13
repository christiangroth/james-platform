package de.chrgroth.james.platform.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

// Renders the real / (login) page against the packaged native executable, anonymously - a basic smoke test that the
// native executable serves HTTP at all before any authenticated page is exercised. See #672.
@QuarkusIntegrationTest
class LoginPageCheckIT {

  @Test
  fun `login page renders successfully for an anonymous request`() {
    given()
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="login-button""""))
  }
}
