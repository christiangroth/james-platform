package de.chrgroth.spotify.control.application.quarkus

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class HealthCheckTests {

  @Test
  fun started() {
    val response = check("started")
    assertThat(response.extract().path<String>("status")).isEqualTo("UP")
    assertThat(response.extract().path<List<Any>>("checks")).isEmpty()
  }

  @Test
  fun well() {
    val response = check("well")
    assertThat(response.extract().path<String>("status")).isEqualTo("UP")
    assertThat(response.extract().path<List<Any>>("checks")).isEmpty()
  }

  @Test
  fun ready() {
    val response = check("ready")
    assertThat(response.extract().path<String>("status")).isEqualTo("UP")
    val checks = response.extract().path<List<Map<String, Any>>>("checks")
    assertThat(checks).hasSize(1)
    assertThat(checks[0]["name"]).isEqualTo("MongoDB connection health check")
    assertThat(checks[0]["status"]).isEqualTo("UP")
  }

  @Test
  fun live() {
    val response = check("live")
    assertThat(response.extract().path<String>("status")).isEqualTo("UP")
    assertThat(response.extract().path<List<Any>>("checks")).isEmpty()
  }

  private fun check(type: String) =
    given()
      .`when`()
      .get("/q/health/$type")
      .then()
      .statusCode(200)
}
