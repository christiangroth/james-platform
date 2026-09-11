package de.chrgroth.james.platform.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

// Renders the real /docs/** pages against the packaged native executable. These markdown files are loaded via plain
// getResourceAsStream (DocsUtils), which native-image excludes from the image by default unless explicitly listed
// via quarkus.native.resources.includes=docs/** - without that property every doc page 404s in native mode while
// working fine on the JVM. See #672.
@QuarkusIntegrationTest
class DocsCheckIT {

  @Test
  fun `arc42 doc page renders successfully`() {
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, SessionCookieForge.forgeSessionCookie("admin"))
      .`when`()
      .get("/docs/arc42/arc42.md")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="breadcrumb-docs""""))
  }

  @Test
  fun `coding guidelines doc page renders successfully`() {
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, SessionCookieForge.forgeSessionCookie("admin"))
      .`when`()
      .get("/docs/coding-guidelines/role-architect.md")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="breadcrumb-docs""""))
  }
}
