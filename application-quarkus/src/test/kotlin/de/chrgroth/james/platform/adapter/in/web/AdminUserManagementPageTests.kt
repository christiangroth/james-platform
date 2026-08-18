package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
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
  fun `user management page displays breadcrumb`() {
    given()
      .`when`()
      .get("/ui/admin/users")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="breadcrumb-users""""))
      .body(containsString("""data-testid="breadcrumb-home-link""""))
      .body(containsString("""href="/ui/admin/dashboard""""))
      .body(not(containsString("""data-testid="breadcrumb-admin-link"""")))
  }

  @Test
  fun `user management page does not display profile or app store links`() {
    given()
      .`when`()
      .get("/ui/admin/users")
      .then()
      .statusCode(200)
      .body(not(containsString("""data-testid="profile-link"""")))
      .body(not(containsString("""data-testid="app-store-link"""")))
  }

  @Test
  fun `users table endpoint returns table fragment`() {
    given()
      .`when`()
      .get("/ui/admin/users/table")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="users-table""""))
  }

  @Test
  fun `create user returns json error on blank password`() {
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("password", "")
      .`when`()
      .put("/ui/admin/users/newuser")
      .then()
      .statusCode(200)
      .contentType(containsString("application/json"))
      .body(containsString(""""ok":false"""))
  }

  @Test
  fun `set password returns json error on blank password`() {
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("newPassword", "")
      .`when`()
      .post("/ui/admin/users/nonexistent/password")
      .then()
      .statusCode(200)
      .contentType(containsString("application/json"))
      .body(containsString(""""ok":false"""))
  }

  @Test
  fun `delete user returns json error for nonexistent user`() {
    given()
      .`when`()
      .delete("/ui/admin/users/nonexistent")
      .then()
      .statusCode(200)
      .contentType(containsString("application/json"))
      .body(containsString(""""ok":false"""))
  }

  @Test
  fun `user status returns stillExists true for an existing user and false for an unknown one`() {
    val username = "status-user-${System.nanoTime()}"
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("password", "password123")
      .`when`()
      .put("/ui/admin/users/$username")
      .then()
      .statusCode(200)

    given()
      .`when`()
      .get("/ui/admin/users/$username/status")
      .then()
      .statusCode(200)
      .contentType(containsString("application/json"))
      .body("stillExists", equalTo(true))

    given()
      .`when`()
      .get("/ui/admin/users/nonexistent/status")
      .then()
      .statusCode(200)
      .body("stillExists", equalTo(false))
  }

  /**
   * Deleting now only enqueues an outbox event (see ADR 0019) instead of deleting synchronously, so the actual
   * removal - the last step of `AdminUserManagementService.handle` - is observed by polling the dedicated status
   * endpoint instead of asserted immediately after the delete call returns. The outbox worker starts at application
   * startup and is signalled on enqueue, so it picks up the task well within this bound under normal test conditions.
   */
  private fun awaitUserDeleted(username: String) {
    val deadlineMs = System.currentTimeMillis() + 5000
    while (System.currentTimeMillis() < deadlineMs) {
      val stillExists = given()
        .`when`()
        .get("/ui/admin/users/$username/status")
        .then()
        .statusCode(200)
        .extract().body().jsonPath().getBoolean("stillExists")
      if (!stillExists) return
      Thread.sleep(50)
    }
    throw AssertionError("Expected user $username to be deleted by the outbox dispatcher within 5s")
  }

  @Test
  fun `delete user enqueues its removal and it disappears in the background`() {
    val username = "delete-user-${System.nanoTime()}"
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("password", "password123")
      .`when`()
      .put("/ui/admin/users/$username")
      .then()
      .statusCode(200)

    given()
      .`when`()
      .delete("/ui/admin/users/$username")
      .then()
      .statusCode(200)
      .contentType(containsString("application/json"))
      .body(containsString(""""ok":true"""))

    awaitUserDeleted(username)

    given()
      .`when`()
      .get("/ui/admin/users/table")
      .then()
      .statusCode(200)
      .body(not(containsString(username)))
  }
}

