package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-error")
class ErrorPageTests {

  @Test
  fun `not found exception renders html error page`() {
    given()
      .`when`()
      .get("/docs/invalid-subdir/some-file.md")
      .then()
      .statusCode(404)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="error-heading""""))
      .body(containsString("404"))
      .body(containsString("""data-testid="error-type""""))
      .body(containsString("""data-testid="error-stack-trace""""))
  }
}
