package de.chrgroth.james.platform.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

// Renders the real /mongodb-viewer page against the packaged native executable. MongoViewerResult/MongoViewerField
// reflect real collection metadata (the "users" collection alone is always non-empty, since the admin bootstrap user
// is always created), including the MongoViewerFieldType enum .name access fixed via EnumTemplateExtensions.
// See #672.
@QuarkusIntegrationTest
class MongoViewerCheckIT {

  @Test
  fun `mongodb viewer page renders successfully with populated collection list`() {
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, SessionCookieForge.forgeSessionCookie("admin"))
      .`when`()
      .get("/mongodb-viewer")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""id="viewer-form""""))
  }
}
