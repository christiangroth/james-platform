package de.chrgroth.james.platform.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

// Renders the real /ui/profile page against the packaged native executable, for the real "admin" user created by
// AdminUserInitializerStarter on startup - exercises the direct java.time.Instant createdAt/lastLoginAt template
// bindings and username rendering. See #672.
@QuarkusIntegrationTest
class ProfileCheckIT {

  @Test
  fun `profile page renders successfully with the real admin username`() {
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, SessionCookieForge.forgeSessionCookie("admin"))
      .`when`()
      .get("/ui/profile")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="profile-username""""))
      .body(containsString("admin"))
  }
}
