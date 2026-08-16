package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-app-store", roles = ["USER"])
class UserAppStoreAccessTests {

  @Test
  fun `user can access app store page`() {
    given()
      .`when`()
      .get("/ui/user/app-store")
      .then()
      .statusCode(200)
  }
}
