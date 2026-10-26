package de.chrgroth.james.platform.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.eclipse.microprofile.config.ConfigProvider
import org.junit.jupiter.api.Test

// Non-application endpoints (/q/*) are served on the management interface only, so Traefik (which routes to the
// public HTTP port) cannot expose them. Verified against the packaged native executable.
@QuarkusIntegrationTest
class ManagementInterfaceIT {

  private val managementPort: Int =
    ConfigProvider.getConfig().getOptionalValue("quarkus.management.test-port", Int::class.java).orElse(9001)

  @Test
  fun `health is served on the management port`() {
    given()
      .port(managementPort)
      .`when`()
      .get("/q/health/live")
      .then()
      .statusCode(200)
  }

  @Test
  fun `metrics are not served on the public HTTP port`() {
    given()
      .`when`()
      .get("/q/metrics")
      .then()
      .statusCode(404)
  }
}
