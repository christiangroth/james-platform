package de.chrgroth.james.platform.application.quarkus

import io.restassured.RestAssured.given
import io.restassured.specification.RequestSpecification
import org.eclipse.microprofile.config.ConfigProvider

/**
 * Non-application endpoints (/q/health, /q/metrics, ...) are served on the Quarkus management interface,
 * not on the public HTTP port (see `quarkus.management.enabled` in application.properties).
 */
object ManagementInterface {

  private const val DEFAULT_TEST_PORT = 9001

  val testPort: Int
    get() = ConfigProvider.getConfig().getOptionalValue("quarkus.management.test-port", Int::class.java).orElse(DEFAULT_TEST_PORT)

  fun givenManagement(): RequestSpecification = given().port(testPort)
}
