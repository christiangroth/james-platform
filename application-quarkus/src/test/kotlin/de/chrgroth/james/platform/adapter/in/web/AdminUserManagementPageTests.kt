package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-admin", roles = ["ADMIN"])
class AdminUserManagementPageTests {

  @Test
  fun `user management page is available and displays users table`() {
    given()
      .`when`()
      .get("/ui/admin/users")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="users-table""""))
  }

  @Test
  fun `user management page displays create user button`() {
    given()
      .`when`()
      .get("/ui/admin/users")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="create-user-button""""))
  }

  @Test
  fun `user management page does not display success or error message by default`() {
    given()
      .`when`()
      .get("/ui/admin/users")
      .then()
      .statusCode(200)
      .body(not(containsString("""data-testid="success-message"""")))
      .body(not(containsString("""data-testid="error-message"""")))
  }

  @Test
  fun `user management success endpoint shows success message`() {
    given()
      .`when`()
      .get("/ui/admin/users/success?msg=user-created")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="success-message""""))
      .body(containsString("User created successfully."))
  }

  @Test
  fun `user management error endpoint shows error message`() {
    given()
      .`when`()
      .get("/ui/admin/users/error?error=ADMIN-002")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="error-message""""))
      .body(containsString("Username already exists."))
  }
}

@QuarkusTest
@TestSecurity(user = "test-user", roles = ["USER"])
class AdminUserManagementPageUnauthorizedTests {

  @Test
  fun `non-admin cannot access user management page`() {
    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/admin/users")
      .then()
      .statusCode(403)
  }
}
