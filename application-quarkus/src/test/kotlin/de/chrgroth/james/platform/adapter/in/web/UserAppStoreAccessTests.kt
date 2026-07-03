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

@QuarkusTest
@TestSecurity(user = "test-admin-app-store", roles = ["ADMIN"])
class AdminAppStoreAccessUnauthorizedTests {

  @Test
  fun `admin cannot access app store page`() {
    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/user/app-store")
      .then()
      .statusCode(403)
  }

  @Test
  fun `admin cannot install app via direct endpoint call`() {
    given()
      .`when`()
      .post("/ui/user/app-store/apps/some-app-id/install")
      .then()
      .statusCode(403)
  }
}
