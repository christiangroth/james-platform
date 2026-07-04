package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.Test

@QuarkusTest
class LanguageSwitchTests {

  @Test
  fun `login page renders german text by default without a language cookie`() {
    given()
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .body(containsString("""<html lang="de">"""))
      .body(containsString("Anmelden"))
      .body(containsString("Benutzername"))
  }

  @Test
  fun `login page renders underscore pseudo locale when lang cookie is xx`() {
    given()
      .cookie("lang", "xx")
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .body(containsString("""<html lang="xx">"""))
      .body(containsString("________"))
      .body(containsString("____________"))
  }

  @Test
  fun `login page falls back to german for an unknown language cookie value`() {
    given()
      .cookie("lang", "fr")
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .body(containsString("""<html lang="de">"""))
      .body(containsString("Anmelden"))
  }

  @Test
  fun `language toggle button is present with both language labels`() {
    given()
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="language-toggle-button""""))
      .body(containsString("""class="language-toggle-label-de""""))
      .body(containsString("""class="language-toggle-label-xx""""))
  }

  @Test
  fun `language toggle button is disabled for anonymous users without monitoring permission`() {
    given()
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="language-toggle-button" title="Sprache wechseln" aria-label="Sprache wechseln" disabled>"""))
  }
}

@QuarkusTest
@TestSecurity(user = "test-monitoring", roles = ["MONITORING"])
class LanguageSwitchMonitoringTests {

  @Test
  fun `language toggle button is enabled for users with monitoring permission`() {
    given()
      .`when`()
      .get("/ui/user/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="language-toggle-button""""))
      .body(not(containsString("""aria-label="Sprache wechseln" disabled>""")))
  }
}

@QuarkusTest
@TestSecurity(user = "test-user", roles = ["USER"])
class LanguageSwitchUserTests {

  @Test
  fun `language toggle button is disabled for users without monitoring permission`() {
    given()
      .`when`()
      .get("/ui/user/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="language-toggle-button" title="Sprache wechseln" aria-label="Sprache wechseln" disabled>"""))
  }
}
