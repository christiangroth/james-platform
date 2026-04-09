package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a")
class RuntimeConfigPageTests {

  @Test
  fun `update throttle interval endpoint accepts valid value`() {
    given()
      .contentType("application/json")
      .body("""{"intervalSeconds":5}""")
      .`when`()
      .post("/settings/runtime-config/throttle-interval")
      .then()
      .statusCode(200)
  }

  @Test
  fun `update throttle interval endpoint rejects negative value`() {
    given()
      .contentType("application/json")
      .body("""{"intervalSeconds":-1}""")
      .`when`()
      .post("/settings/runtime-config/throttle-interval")
      .then()
      .statusCode(400)
  }

  @Test
  fun `update throttle interval to zero is allowed`() {
    given()
      .contentType("application/json")
      .body("""{"intervalSeconds":0}""")
      .`when`()
      .post("/settings/runtime-config/throttle-interval")
      .then()
      .statusCode(200)
  }
}
