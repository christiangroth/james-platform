package de.chrgroth.james.platform.application.quarkus

import de.chrgroth.james.platform.application.quarkus.ManagementInterface.givenManagement
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class MetricsTests {

  @Test
  fun `check top-level metrics groups`() {
    val metrics = givenManagement()
      .`when`()
      .get("/q/metrics")
      .then()
      .statusCode(200)
      .extract()
      .asString()

    val topLevelGroups = metrics.lines()
      .filter { it.isNotBlank() }
      .filterNot { it.startsWith("#") }
      .map { it.split("_")[0] }
      .distinct()
      .sorted()
    assertThat(topLevelGroups).isEqualTo(listOf("http", "import", "jvm", "log", "mongodb", "netty", "outbox", "process", "script", "system", "worker"))
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
