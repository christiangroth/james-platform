package de.chrgroth.james.platform.adapter.`in`.http;

import io.quarkus.security.Authenticated
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.http.Cookie
import io.restassured.matcher.RestAssuredMatchers
import io.restassured.response.ValidatableResponse
import io.restassured.specification.RequestSpecification
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.Matcher
import org.hamcrest.Matchers.emptyOrNullString
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.lessThan
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date


@QuarkusTest
class AuthenticationTests {

  @TestHTTPResource
  lateinit var rootUrl: String

  @Test
  fun `invalid login is denied`() {
    login("unknown", "invalid").then()
      .statusCode(302)
      .header("location", "${rootUrl}ui/login?loginError=true")
      .cookies(emptyMap<String, Any>())
  }

  @Test
  fun `valid login creates cookie`() {
    val response = login("admin", "admin").then()
      .statusCode(302)
      .header("location", "${rootUrl}ui/dashboard")
      .assertCredentialsCookie(
        valueMatcher = not(`is`(emptyOrNullString())),
        requestTime = Instant.now(),
      ).extract()

    assertThat(response.cookies()).doesNotContainKeys("quarkus-redirect-location")
  }

  @Test
  fun `double login is ignored`() {
    val loginResponse = login("admin", "admin")

    val response = login("admin", "admin") {
      it.cookie(loginResponse.cookie("credential"))
    }.then()
      .statusCode(302)
      .header("location", "${rootUrl}ui/dashboard")
      .assertCredentialsCookie(
        valueMatcher = not(`is`(emptyOrNullString())),
        requestTime = Instant.now(),
      )
      .extract()

    assertThat(response.cookies()).doesNotContainKeys("quarkus-redirect-location")
  }

  @Authenticated
  @Path("/AuthenticationTests/endpoint")
  @Suppress("Unused")
  class AuthenticatedTestResource {

    @GET
    @Produces("text/plain")
    @Suppress("FunctionOnlyReturningConstant")
    fun greet(): String {
      return ""
    }
  }

  @Test
  @Disabled("Receives 404 as soon as the endpoint is somewhat secured, or 200 it not secured")
  fun `redirect after login is executed`() {
    val initialPageResponse = given()
      .headers("accept", "*")
      .`when`()
      .get("/AuthenticationTests/endpoint")
      .then()
      .statusCode(302)
      .header("location", "${rootUrl}login?loginError=true")
      .cookies(
        mapOf(
          "quarkus-redirect-location" to "/AuthenticationTests/endpoint"
        )
      )
      .extract()

    val response = login("admin", "admin") {
      it.cookie(initialPageResponse.cookie("quarkus-redirect-location"))
    }.then()
      .statusCode(302)
      .header("location", "${rootUrl}AuthenticationTests/endpoint")
      .assertCredentialsCookie(
        valueMatcher = not(`is`(emptyOrNullString())),
        requestTime = Instant.now(),
      )
      .extract()

    assertThat(response.cookies()).doesNotContainKeys("quarkus-redirect-location")
  }

  @Test
  fun `logout without login is ignored`() {
    val response = logout(null).then()
      .statusCode(302)
      .header("location", "${rootUrl}login")
      .assertInvalidatedCredentialsCookie()
      .extract()

    assertThat(response.cookies()).doesNotContainKeys("quarkus-redirect-location")
  }

  @Test
  fun `valid logout invalidates cookie`() {
    val loginResponse = login("admin", "admin")

    val response = logout(loginResponse.detailedCookie("credential")).then()
      .statusCode(302)
      .header("location", "${rootUrl}login")
      .assertInvalidatedCredentialsCookie()
      .extract()

    assertThat(response.cookies()).doesNotContainKeys("quarkus-redirect-location")
  }

  private fun login(username: String, password: String, extraConfig: (RequestSpecification) -> Unit = {}) =
    given()
      .headers(
        "content-type", "application/x-www-form-urlencoded",
        "accept", "*",
      )
      .formParams(
        "username", username,
        "password", password
      )
      .also(extraConfig)
      .`when`()
      .post("/auth/login")

  private fun logout(cookie: Cookie?) = given()
    .headers(
      "content-type", "application/x-www-form-urlencoded",
      "accept", "*",
    )
    .also {
      if (cookie != null) {
        it.cookie(cookie)
      }
    }
    .`when`()
    .get("/auth/logout")

  private fun ValidatableResponse.assertCredentialsCookie(
    valueMatcher: Matcher<String>,
    requestTime: Instant,
  ): ValidatableResponse {
    val cookieExpiryTime: Instant = requestTime.plus(3, ChronoUnit.DAYS)
    val cookieExpiryTimeMin: Date = Date.from(cookieExpiryTime.minusSeconds(20))
    val cookieExpiryTimeMax: Date = Date.from(cookieExpiryTime.plusSeconds(20))

    return cookie(
      "credential",
      RestAssuredMatchers.detailedCookie()
        .value(valueMatcher)
        .domain(`is`(nullValue()))
        .comment(`is`(nullValue()))
        .expiryDate(`is`(greaterThan(cookieExpiryTimeMin)))
        .expiryDate(`is`(lessThan(cookieExpiryTimeMax)))
        .httpOnly(`is`(true))
        .maxAge(EXPECTED_COOKIE_MAX_AGE)
        .path(`is`("/"))
        .sameSite(`is`("Strict"))
        .secured(`is`(false))
    )
  }

  private fun ValidatableResponse.assertInvalidatedCredentialsCookie(): ValidatableResponse {
    return cookie(
      "credential",
      RestAssuredMatchers.detailedCookie()
        .value(`is`(emptyOrNullString()))
        .domain(`is`(nullValue()))
        .comment(`is`(nullValue()))
        .expiryDate(`is`(Date.from(Instant.EPOCH)))
        .httpOnly(`is`(true))
        .maxAge(0)
        .path(`is`("/"))
        .sameSite(`is`("Strict"))
        .secured(`is`(false))
    )
  }

  companion object {
    private const val EXPECTED_COOKIE_MAX_AGE: Long = 3 * 24 * 60 * 60

    @JvmStatic
    @BeforeAll
    fun setup() {
      RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()
    }
  }
}
