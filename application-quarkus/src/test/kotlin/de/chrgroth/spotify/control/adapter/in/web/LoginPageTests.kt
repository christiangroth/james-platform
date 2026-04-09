package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.Test

@QuarkusTest
class LoginPageTests {

  @Test
  fun `login page is available and displays login button`() {
    given()
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="login-button""""))
  }

  @Test
  fun `login page displays dynamic app version from build`() {
    given()
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .body(containsString("""class="app-version"""))
      .body(not(containsString("@projectVersion@")))
  }

  @Test
  fun `login page does not display health indicator icons`() {
    given()
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .body(not(containsString("""id="navbar-health-indicators"""")))
      .body(not(containsString("""data-testid="navbar-outbox-icon"""")))
      .body(not(containsString("""data-testid="navbar-playback-icon"""")))
  }

  @Test
  fun `login page does not create navbar sse connection`() {
    given()
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .body(not(containsString("updateNavbarOutboxStatus")))
      .body(not(containsString("updateNavbarPlaybackStatus")))
  }
}
