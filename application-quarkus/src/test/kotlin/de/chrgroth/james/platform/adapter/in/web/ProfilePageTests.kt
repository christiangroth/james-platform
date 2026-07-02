package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-profile")
class ProfilePageTests {

  @Test
  fun `profile page is available and displays account info heading`() {
    given()
      .`when`()
      .get("/ui/profile")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="account-info-heading""""))
  }

  @Test
  fun `profile page displays username`() {
    given()
      .`when`()
      .get("/ui/profile")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="profile-username""""))
      .body(containsString("test-user-profile"))
  }

  @Test
  fun `profile page displays created at`() {
    given()
      .`when`()
      .get("/ui/profile")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="profile-created-at""""))
  }

  @Test
  fun `profile page displays last login at`() {
    given()
      .`when`()
      .get("/ui/profile")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="profile-last-login-at""""))
  }

  @Test
  fun `profile page displays change username form`() {
    given()
      .`when`()
      .get("/ui/profile")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="change-username-button""""))
      .body(containsString("""data-testid="new-username-input""""))
      .body(containsString("""action="/ui/profile/username""""))
  }

  @Test
  fun `profile page displays change password form`() {
    given()
      .`when`()
      .get("/ui/profile")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="change-password-button""""))
      .body(containsString("""data-testid="current-password-input""""))
      .body(containsString("""data-testid="new-password-input""""))
      .body(containsString("""data-testid="confirm-password-input""""))
      .body(containsString("""action="/ui/profile/password""""))
  }

  @Test
  fun `profile page contains profile link in navbar`() {
    given()
      .`when`()
      .get("/ui/profile")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="profile-link""""))
  }

  @Test
  fun `profile page success endpoint shows success message`() {
    given()
      .`when`()
      .get("/ui/profile/success?msg=password-changed")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="success-message""""))
      .body(containsString("Passwort erfolgreich geändert."))
  }

  @Test
  fun `profile page error endpoint shows error message`() {
    given()
      .`when`()
      .get("/ui/profile/error?error=PROFILE-003")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="error-message""""))
      .body(containsString("Das aktuelle Passwort ist falsch."))
  }

  @Test
  fun `profile page does not display success or error message by default`() {
    given()
      .`when`()
      .get("/ui/profile")
      .then()
      .statusCode(200)
      .body(not(containsString("""data-testid="success-message"""")))
      .body(not(containsString("""data-testid="error-message"""")))
  }
}

@QuarkusTest
class ProfilePageUnauthenticatedTests {

  @Test
  fun `unauthenticated access to profile page redirects`() {
    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/profile")
      .then()
      .statusCode(307)
  }
}
