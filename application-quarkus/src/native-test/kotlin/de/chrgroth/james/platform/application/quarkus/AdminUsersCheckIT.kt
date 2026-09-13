package de.chrgroth.james.platform.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

// Renders the real /ui/admin/users page against the packaged native executable. The users list is naturally
// non-empty as soon as the app has booted (the "admin" bootstrap user always exists), exercising the User Qute
// binding and the UserRole enum .name access fixed via EnumTemplateExtensions. See #672.
@QuarkusIntegrationTest
class AdminUsersCheckIT {

  @Test
  fun `admin users page renders successfully with populated users table`() {
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, SessionCookieForge.forgeSessionCookie("admin"))
      .`when`()
      .get("/ui/admin/users")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="users-table""""))
      .body(containsString("admin"))
  }
}
