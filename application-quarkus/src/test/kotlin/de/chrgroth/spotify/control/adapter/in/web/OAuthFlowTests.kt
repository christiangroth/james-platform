package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.port.out.user.TokenEncryptionPort
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.junit.jupiter.api.Test

@QuarkusTest
class OAuthFlowTests {

  @Inject
  private lateinit var tokenEncryption: TokenEncryptionPort

  @Test
  fun `authorize endpoint redirects to spotify mock`() {
    given()
      .redirects().follow(false)
      .`when`()
      .get("/oauth/authorize")
      .then()
      .statusCode(307)
      .header("Location", org.hamcrest.CoreMatchers.containsString("http://localhost:8081/mock/spotify/authorize"))
  }

  @Test
  fun `callback with error redirects to login`() {
    given()
      .redirects().follow(false)
      .queryParam("error", "access_denied")
      .`when`()
      .get("/oauth/callback")
      .then()
      .statusCode(307)
      .header("Location", org.hamcrest.CoreMatchers.containsString("error=OAUTH-001"))
  }

  @Test
  fun `callback with missing params redirects to login`() {
    given()
      .redirects().follow(false)
      .`when`()
      .get("/oauth/callback")
      .then()
      .statusCode(307)
      .header("Location", org.hamcrest.CoreMatchers.containsString("error=OAUTH-002"))
  }

  @Test
  fun `callback with invalid state redirects to login`() {
    given()
      .redirects().follow(false)
      .queryParam("code", "some-code")
      .queryParam("state", "invalid-state")
      .`when`()
      .get("/oauth/callback")
      .then()
      .statusCode(307)
      .header("Location", org.hamcrest.CoreMatchers.containsString("error=OAUTH-003"))
  }

  @Test
  fun `full oauth callback flow succeeds for allowed user`() {
    // Trigger authorize to get a valid state
    val authorizeResponse = given()
      .redirects().follow(false)
      .`when`()
      .get("/oauth/authorize")
      .then()
      .statusCode(307)
      .extract()
      .response()

    val location = authorizeResponse.getHeader("Location")
    val state = location.substringAfter("state=").substringBefore("&")

    // Simulate callback with valid state
    val callbackResponse = given()
      .redirects().follow(false)
      .queryParam("code", "mock-auth-code")
      .queryParam("state", state)
      .`when`()
      .get("/oauth/callback")
      .then()
      .statusCode(307)
      .header("Location", org.hamcrest.CoreMatchers.endsWith("/dashboard"))
      .extract()
      .response()

    // Session cookie should be set with a positive maxAge (persistent, not session-only)
    val sessionCookie = callbackResponse.getDetailedCookie("spotify-session")
    assert(sessionCookie != null && sessionCookie.value.isNotEmpty())
    assert(sessionCookie.maxAge > 0) { "Session cookie must have a positive maxAge to persist across browser restarts" }
  }

  @Test
  fun `index page redirects to dashboard with valid session cookie`() {
    // First: do full OAuth flow to register the user in the database
    val authorizeResponse = given()
      .redirects().follow(false)
      .`when`()
      .get("/oauth/authorize")
      .then()
      .statusCode(307)
      .extract()
      .response()

    val location = authorizeResponse.getHeader("Location")
    val state = location.substringAfter("state=").substringBefore("&")

    val callbackResponse = given()
      .redirects().follow(false)
      .queryParam("code", "mock-auth-code")
      .queryParam("state", state)
      .`when`()
      .get("/oauth/callback")
      .then()
      .statusCode(307)
      .extract()
      .response()

    val sessionCookie = callbackResponse.cookie("spotify-session")

    // Hitting index with a valid session cookie should redirect to dashboard
    given()
      .cookie("spotify-session", sessionCookie)
      .redirects().follow(false)
      .`when`()
      .get("/")
      .then()
      .statusCode(307)
      .header("Location", org.hamcrest.CoreMatchers.endsWith("/dashboard"))
  }

  @Test
  fun `logout clears session and redirects to root`() {
    val cookieValue = (tokenEncryption.encrypt("test-user-a") as arrow.core.Either.Right).value

    given()
      .cookie("spotify-session", cookieValue)
      .redirects().follow(false)
      .`when`()
      .get("/logout")
      .then()
      .statusCode(307)
      .header("Location", org.hamcrest.CoreMatchers.endsWith("/"))
  }

  @Test
  fun `protected route redirects to login without session`() {
    given()
      .redirects().follow(false)
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(307)
      .header("Location", org.hamcrest.CoreMatchers.endsWith("/"))
  }
}

