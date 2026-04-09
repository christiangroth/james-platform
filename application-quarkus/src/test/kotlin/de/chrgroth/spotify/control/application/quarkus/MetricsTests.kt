package de.chrgroth.spotify.control.application.quarkus

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class MetricsTests {

  @Test
  fun `check top-level metrics groups`() {
    val metrics = given()
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
    assertThat(topLevelGroups).isEqualTo(listOf("http", "jvm", "mongodb", "netty", "outbox", "process", "spotify", "system", "worker"))
  }
}
