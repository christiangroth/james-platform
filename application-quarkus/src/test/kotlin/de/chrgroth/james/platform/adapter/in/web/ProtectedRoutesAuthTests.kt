package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

// Central, parameterized coverage for the two cross-cutting checks every protected route must satisfy:
// unauthenticated access is redirected to the login page, and access with an insufficient or
// explicitly forbidden role is denied. Page-specific test files should not re-assert these, see #563.

@QuarkusTest
class UnauthenticatedAccessRedirectsToLoginTests {

  @ParameterizedTest
  @ValueSource(
    strings = [
      "/ui/user/dashboard",
      "/ui/admin/dashboard",
      "/ui/admin/users",
      "/ui/developer/dashboard",
      "/ui/developer/apps/some-id",
      "/ui/profile",
      "/ui/user/imports",
      "/ui/user/imports/connections",
      "/ui/user/app-store",
    ],
  )
  fun `unauthenticated access to protected route redirects to login`(path: String) {
    given()
      .redirects().follow(false)
      .`when`()
      .get(path)
      .then()
      .statusCode(307)
  }
}

@QuarkusTest
@TestSecurity(user = "test-admin-blocked", roles = ["ADMIN"])
class AdminBlockedFromUserRoutesTests {

  @ParameterizedTest
  @ValueSource(strings = ["/ui/user/dashboard", "/ui/user/app-store"])
  fun `admin cannot access user-space route`(path: String) {
    given()
      .redirects().follow(false)
      .`when`()
      .get(path)
      .then()
      .statusCode(403)
  }

  @Test
  fun `admin cannot install an app via direct endpoint call`() {
    given()
      .`when`()
      .post("/ui/user/app-store/apps/some-app-id/install")
      .then()
      .statusCode(403)
  }
}

@QuarkusTest
@TestSecurity(user = "test-user-blocked", roles = ["USER"])
class UserBlockedFromAdminRoutesTests {

  @ParameterizedTest
  @ValueSource(strings = ["/ui/admin/dashboard", "/ui/admin/users"])
  fun `non-admin cannot access admin route`(path: String) {
    given()
      .redirects().follow(false)
      .`when`()
      .get(path)
      .then()
      .statusCode(403)
  }
}

@QuarkusTest
@TestSecurity(user = "test-developer-blocked", roles = ["DEVELOPER"])
class DeveloperBlockedFromDataImportRoutesTests {

  @ParameterizedTest
  @ValueSource(strings = ["/ui/user/imports", "/ui/user/imports/connections"])
  fun `developer without DATA_IMPORT role cannot access import route`(path: String) {
    given()
      .`when`()
      .get(path)
      .then()
      .statusCode(403)
  }

  @Test
  fun `developer without DATA_IMPORT role cannot create an import connection`() {
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Should Not Work")
      .formParam("baseUrl", "https://example.com/data")
      .`when`()
      .post("/ui/user/imports/connections")
      .then()
      .statusCode(403)
  }
}
