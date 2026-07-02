package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.ws.rs.core.MediaType
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
  fun `login page displays username and password fields`() {
    given()
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .body(containsString("""name="username""""))
      .body(containsString("""name="password""""))
      .body(containsString("""action="/login""""))
  }

  @Test
  fun `login page displays german login texts from message bundle`() {
    given()
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .body(containsString("Anmelden"))
      .body(containsString("Benutzername"))
      .body(containsString("Passwort"))
  }

  @Test
  fun `login page displays James Platform title`() {
    given()
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .body(containsString("James Platform"))
  }

  @Test
  fun `login page displays German hero tagline resolved from message bundle`() {
    given()
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .body(containsString("Low-Code-Plattform"))
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
      .body(not(containsString("""data-testid="navbar-playback-icon"""")))
  }

  @Test
  fun `login page does not create navbar sse connection`() {
    given()
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .body(not(containsString("updateNavbarPlaybackStatus")))
  }
}

@QuarkusTest
class LoginRedirectTests {

  @Test
  fun `login with blank username redirects to login page with error using see other`() {
    given()
      .redirects().follow(false)
      .contentType(MediaType.APPLICATION_FORM_URLENCODED)
      .formParam("username", "")
      .formParam("password", "secret")
      .`when`()
      .post("/login")
      .then()
      .statusCode(303)
      .header("Location", containsString("/?error="))
  }

  @Test
  fun `login with blank password redirects to login page with error using see other`() {
    given()
      .redirects().follow(false)
      .contentType(MediaType.APPLICATION_FORM_URLENCODED)
      .formParam("username", "admin")
      .formParam("password", "")
      .`when`()
      .post("/login")
      .then()
      .statusCode(303)
      .header("Location", containsString("/?error="))
  }

  @Test
  fun `login with invalid credentials redirects to login page with error using see other`() {
    given()
      .redirects().follow(false)
      .contentType(MediaType.APPLICATION_FORM_URLENCODED)
      .formParam("username", "unknown-user")
      .formParam("password", "wrong-password")
      .`when`()
      .post("/login")
      .then()
      .statusCode(303)
      .header("Location", containsString("/?error="))
  }

  @Test
  fun `login with invalid credentials displays german error message from message bundle`() {
    given()
      .redirects().follow(true)
      .contentType(MediaType.APPLICATION_FORM_URLENCODED)
      .formParam("username", "unknown-user")
      .formParam("password", "wrong-password")
      .`when`()
      .post("/login")
      .then()
      .statusCode(200)
      .body(containsString("Ungültiger Benutzername oder ungültiges Passwort"))
  }
}
