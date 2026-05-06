package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a", roles = ["ADMIN"])
class DashboardPageTests {

  @Test
  fun `user dashboard page is available and displays apps grid`() {
    given()
      .`when`()
      .get("/ui/user/dashboard")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="welcome-message""""))
      .body(containsString("""data-testid="apps-grid""""))
  }

  @Test
  fun `developer dashboard page is available and displays breadcrumb`() {
    given()
      .`when`()
      .get("/ui/developer/dashboard")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="breadcrumb-developer""""))
  }

  @Test
  fun `admin dashboard page is available and displays user count tile`() {
    given()
      .`when`()
      .get("/ui/admin/dashboard")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="user-count-tile""""))
  }

  @Test
  fun `dashboard pages display logout link`() {
    given()
      .`when`()
      .get("/ui/admin/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="logout-link""""))
  }

  @Test
  fun `dashboard pages display github link with new repo url`() {
    given()
      .`when`()
      .get("/ui/admin/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="github-link""""))
      .body(containsString("https://github.com/christiangroth/james-platform"))
  }

  @Test
  fun `dashboard pages do not display spotify api link`() {
    given()
      .`when`()
      .get("/ui/admin/dashboard")
      .then()
      .statusCode(200)
      .body(not(containsString("""data-testid="spotify-api-link""")))
  }

  @Test
  fun `dashboard pages display grafana links with new base url`() {
    given()
      .`when`()
      .get("/ui/admin/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("jamesplatform.grafana.net"))
  }

  @Test
  fun `dashboard pages display updated mongodb atlas link`() {
    given()
      .`when`()
      .get("/ui/admin/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("cloud.mongodb.com/v2/69d66ace6a6c9a904f96cdd5"))
  }

  @Test
  fun `dashboard pages do not display playback status badge`() {
    given()
      .`when`()
      .get("/ui/admin/dashboard")
      .then()
      .statusCode(200)
      .body(not(containsString("""data-testid="navbar-playback-icon""")))
  }
  @Test
  fun `user dashboard page does not display app store tile`() {
    given()
      .`when`()
      .get("/ui/user/dashboard")
      .then()
      .statusCode(200)
      .body(not(containsString("""data-testid="app-store-tile""")))  }
}

@QuarkusTest
class DashboardPageUnauthenticatedTests {

  @Test
  fun `unauthenticated access to dashboard redirects to login`() {
    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/user/dashboard")
      .then()
      .statusCode(307)
  }
}

@QuarkusTest
@TestSecurity(user = "test-developer", roles = ["DEVELOPER"])
class DeveloperNavbarLinkTests {

  @Test
  fun `developer dashboard link is shown in navbar for developer role`() {
    given()
      .`when`()
      .get("/ui/user/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="developer-dashboard-link""""))
  }

  @Test
  fun `app store link is shown in navbar for developer role`() {
    given()
      .`when`()
      .get("/ui/user/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="app-store-link""""))
  }
}

@QuarkusTest
@TestSecurity(user = "test-user-b", roles = ["USER"])
class NonDeveloperNavbarLinkTests {

  @Test
  fun `developer dashboard link is not shown in navbar for user role`() {
    given()
      .`when`()
      .get("/ui/user/dashboard")
      .then()
      .statusCode(200)
      .body(not(containsString("""data-testid="developer-dashboard-link""")))
  }

  @Test
  fun `app store link is shown in navbar for user role`() {
    given()
      .`when`()
      .get("/ui/user/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="app-store-link""""))
  }
}

@QuarkusTest
@TestSecurity(user = "test-monitoring", roles = ["MONITORING"])
class MonitoringNavbarLinkTests {

  @Test
  fun `tools menu is shown in navbar for monitoring role`() {
    given()
      .`when`()
      .get("/ui/user/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="technical-menu""""))
  }
}

@QuarkusTest
@TestSecurity(user = "test-user-c", roles = ["USER"])
class UserNavbarToolsMenuTests {

  @Test
  fun `tools menu is not shown in navbar for user role`() {
    given()
      .`when`()
      .get("/ui/user/dashboard")
      .then()
      .statusCode(200)
      .body(not(containsString("""data-testid="technical-menu""")))
  }
}
