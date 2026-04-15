package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-developer", roles = ["DEVELOPER"])
class DeveloperAppPageTests {

  @Test
  fun `developer dashboard displays apps grid and new-app tile`() {
    given()
      .`when`()
      .get("/ui/developer/dashboard")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="welcome-message""""))
      .body(containsString("test-developer"))
      .body(containsString("""data-testid="apps-grid""""))
      .body(containsString("""data-testid="new-app-tile""""))
  }

  @Test
  fun `developer dashboard does not show static error message`() {
    given()
      .`when`()
      .get("/ui/developer/dashboard")
      .then()
      .statusCode(200)
      .body(not(containsString("""data-testid="error-message""")))
  }

  @Test
  fun `create app returns error json on blank name`() {
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .contentType(containsString("application/json"))
      .body(containsString("\"ok\":false"))
  }

  @Test
  fun `create app returns success json with redirect on valid name`() {
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Test App ${System.nanoTime()}")
      .formParam("description", "A test app")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .contentType(containsString("application/json"))
      .body(containsString("\"ok\":true"))
      .body(containsString("/ui/developer/apps/"))
  }

  @Test
  fun `app overview redirects to dashboard for unknown app`() {
    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/developer/apps/unknown-id")
      .then()
      .statusCode(303)
      .header("Location", containsString("/ui/developer/dashboard"))
  }

  @Test
  fun `version editor redirects to dashboard for unknown app`() {
    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/developer/apps/unknown-id/versions/unknown-version-id")
      .then()
      .statusCode(303)
      .header("Location", containsString("/ui/developer/dashboard"))
  }

  @Test
  fun `entity editor redirects to dashboard for unknown app`() {
    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/developer/apps/unknown-id/versions/unknown-version-id/entities/unknown-entity-id")
      .then()
      .statusCode(303)
      .header("Location", containsString("/ui/developer/dashboard"))
  }

  @Test
  fun `report editor redirects to dashboard for unknown app`() {
    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/developer/apps/unknown-id/versions/unknown-version-id/reports/unknown-report-id")
      .then()
      .statusCode(303)
      .header("Location", containsString("/ui/developer/dashboard"))
  }
}

@QuarkusTest
class DeveloperAppPageUnauthenticatedTests {

  @Test
  fun `unauthenticated access to developer dashboard redirects to login`() {
    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/developer/dashboard")
      .then()
      .statusCode(307)
  }

  @Test
  fun `unauthenticated access to app overview redirects to login`() {
    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/developer/apps/some-id")
      .then()
      .statusCode(307)
  }
}
