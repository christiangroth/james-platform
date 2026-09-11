package de.chrgroth.james.platform.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

// Renders the real /release-notes page against the packaged native executable. Reads and parses the real
// RELEASENOTES.md (naturally non-empty - real project history), exercising ReleaseNotesEntry/
// ReleaseNotesMinorVersionGroup Qute bindings, and the docs/** native-image resource bundling fixed via
// quarkus.native.resources.includes. See #672.
@QuarkusIntegrationTest
class ReleaseNotesCheckIT {

  @Test
  fun `release notes page renders successfully with populated entries`() {
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, SessionCookieForge.forgeSessionCookie("admin"))
      .`when`()
      .get("/release-notes")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="release-notes-group""""))
      .body(containsString("""data-testid="release-notes-entry""""))
  }
}
