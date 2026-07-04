package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a", roles = ["ADMIN"])
class ConfigPageTests {

  @Test
  fun `config page is available and displays configuration heading`() {
    given()
      .`when`()
      .get("/config")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Konfiguration"))
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
      .body(containsString("Schlüssel"))
      .body(containsString("Wert"))
  }

  @Test
  fun `config page config table subtitle is Config`() {
    given()
      .`when`()
      .get("/config")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="config-table""""))
      .body(containsString("Konfiguration"))
  }

  @Test
  fun `config page environment table subtitle is Environment`() {
    given()
      .`when`()
      .get("/config")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="env-table""""))
      .body(containsString("Umgebung"))
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

  @Test
  fun `config page contains logs ui link in navbar`() {
    given()
      .`when`()
      .get("/config")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="logs-ui-link""""))
  }

  @Test
  fun `config page contains breadcrumb with admin home and active config item`() {
    given()
      .`when`()
      .get("/config")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="breadcrumb-home-link""""))
      .body(containsString("""href="/ui/admin/dashboard""""))
      .body(containsString("""data-testid="breadcrumb-config""""))
      .body(containsString("Konfiguration"))
  }
}
