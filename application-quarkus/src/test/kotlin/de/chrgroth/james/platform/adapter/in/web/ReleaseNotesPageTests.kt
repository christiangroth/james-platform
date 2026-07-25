package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a")
class ReleaseNotesPageTests {

  @Test
  fun `release notes page is available and displays heading`() {
    given()
      .`when`()
      .get("/release-notes")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Release Notes"))
  }

  @Test
  fun `release notes page renders an accordion group per minor version`() {
    given()
      .`when`()
      .get("/release-notes")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="release-notes-group""""))
      .body(containsString("""data-testid="release-notes-entry""""))
      .body(containsString("0.66.x"))
  }

  @Test
  fun `release notes page uses marked to render section content client side`() {
    given()
      .`when`()
      .get("/release-notes")
      .then()
      .statusCode(200)
      .body(containsString("release-notes-raw"))
      .body(containsString("marked.parse("))
      .body(containsString("marked.umd.js"))
  }

  @Test
  fun `release notes page displays new features and bugfixes section headings`() {
    given()
      .`when`()
      .get("/release-notes")
      .then()
      .statusCode(200)
      .body(containsString("Neue Funktionen"))
      .body(containsString("Bugfixes / Chore"))
  }

  @Test
  fun `release notes page contains breadcrumb with user home and active item`() {
    given()
      .`when`()
      .get("/release-notes")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="breadcrumb-home-link""""))
      .body(containsString("""href="/ui/user/dashboard""""))
      .body(containsString("""data-testid="breadcrumb-release-notes""""))
  }

  @Test
  @TestSecurity(user = "test-admin", roles = ["ADMIN"])
  fun `release notes link is available in navbar technical dropdown`() {
    given()
      .`when`()
      .get("/release-notes")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="release-notes-link""""))
      .body(containsString("""href="/release-notes""""))
  }

  @Test
  fun `navbar app version link points to the release notes page`() {
    given()
      .`when`()
      .get("/release-notes")
      .then()
      .statusCode(200)
      .body(containsString("""href="/release-notes" class="app-version me-2"""))
      .body(containsString("""data-testid="version-link""""))
  }
}
