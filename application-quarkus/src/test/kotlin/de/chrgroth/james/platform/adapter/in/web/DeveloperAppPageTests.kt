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
  fun `developer dashboard shows error message when error param present`() {
    given()
      .`when`()
      .get("/ui/developer/dashboard?error=APP-003")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="error-message""""))
  }

  @Test
  fun `developer dashboard does not show error message without error param`() {
    given()
      .`when`()
      .get("/ui/developer/dashboard")
      .then()
      .statusCode(200)
      .body(not(containsString("""data-testid="error-message""")))
  }

  @Test
  fun `create app redirects to dashboard on blank name`() {
    given()
      .redirects().follow(false)
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(303)
      .header("Location", containsString("/ui/developer/dashboard"))
  }

  @Test
  fun `create app redirects to app overview on success`() {
    given()
      .redirects().follow(false)
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Test App ${System.nanoTime()}")
      .formParam("description", "A test app")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(303)
      .header("Location", containsString("/ui/developer/apps/"))
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
