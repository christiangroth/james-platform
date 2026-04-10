package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a")
class ConfigPageTests {

  @Test
  fun `config page is available and displays configuration heading`() {
    given()
      .`when`()
      .get("/config")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Configuration"))
  }

  @Test
  fun `config page contains config and environment tables`() {
    given()
      .`when`()
      .get("/config")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="config-table""""))
      .body(containsString("""data-testid="env-table""""))
  }

  @Test
  fun `config page tables show key and value columns`() {
    given()
      .`when`()
      .get("/config")
      .then()
      .statusCode(200)
      .body(containsString("Key"))
      .body(containsString("Value"))
  }

  @Test
  fun `config page config table subtitle is Config`() {
    given()
      .`when`()
      .get("/config")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="config-table""""))
      .body(containsString("Config"))
  }

  @Test
  fun `config page environment table subtitle is Environment`() {
    given()
      .`when`()
      .get("/config")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="env-table""""))
      .body(containsString("Environment"))
  }

  @Test
  fun `config page config table does not contain masking config keys`() {
    given()
      .`when`()
      .get("/config")
      .then()
      .statusCode(200)
      .body(not(containsString("app.health.masked-config-keys")))
      .body(not(containsString("app.health.masked-env-keys")))
  }

  @Test
  fun `config page masks sensitive config values`() {
    given()
      .`when`()
      .get("/config")
      .then()
      .statusCode(200)
      .body(containsString("app.token-encryption-key"))
      .body(not(containsString("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")))
  }

  @Test
  fun `config page does not contain profile-specific config keys`() {
    given()
      .`when`()
      .get("/config")
      .then()
      .statusCode(200)
      .body(not(containsString("%dev.")))
      .body(not(containsString("%test.")))
  }

  @Test
  fun `config page contains health link in navbar`() {
    given()
      .`when`()
      .get("/config")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="health-link""""))
  }

  @Test
  fun `config page contains config link in navbar`() {
    given()
      .`when`()
      .get("/config")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="config-link""""))
  }
}
