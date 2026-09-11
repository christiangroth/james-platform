package de.chrgroth.james.platform.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Drives a full, real developer + user flow against the packaged native executable: create an app, add a draft
 * version with an entity and properties, publish it, install it, and create one real app-data record - then renders
 * every page along the way. This is deliberately NOT a fresh/empty-state check: the whole point (see #672) is that
 * empty lists don't exercise the per-item Qute reflection path GraalVM native-image struggles with, so the pages
 * asserted here are asserted against real, non-empty App/AppVersion/EntityDefinition/Property/InstalledApp/
 * InstalledAppInfo/AppDataDetail/AppDataPropertyView object graphs - the largest and riskiest untyped
 * Template.data() bindings in the app outside of /health.
 *
 * Authenticates as the real "admin" bootstrap user (forged session cookie, see SessionCookieForge) for the developer
 * steps - DeveloperAppResource is merely @Authenticated, no role restriction. The user-facing steps (app store,
 * install, dashboard, app data) need a separate non-admin account though: UserAppStoreResource and the user
 * dashboard are annotated @BlockAdminAccess (BlockAdminAccessFilter.kt) and return 403 for the ADMIN role even
 * though it's otherwise authenticated - so a second user is created via the real admin "create user" endpoint
 * (default role USER, sufficient for every page exercised here) and used for that half of the flow.
 */
@QuarkusIntegrationTest
class DeveloperAndUserFlowIT {

  @Test
  fun `developer creates and publishes an app, user installs it and creates data, all pages render`() {
    val adminCookie = SessionCookieForge.forgeSessionCookie("admin")
    val appName = "Native Test App ${System.nanoTime()}"
    val testUsername = "native-test-user-${System.nanoTime()}"

    // create a plain USER-role account (as admin) for the user-facing half of the flow below
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, adminCookie)
      .contentType("application/x-www-form-urlencoded")
      .formParam("password", "native-test-password-1!")
      .`when`()
      .put("/ui/admin/users/$testUsername")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    val userCookie = SessionCookieForge.forgeSessionCookie(testUsername)

    // developer dashboard renders with the (still empty, at this point) apps grid
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, adminCookie)
      .`when`()
      .get("/ui/developer/dashboard")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="apps-grid""""))
      .body(containsString("""data-testid="new-app-tile""""))

    // create app
    val appId = given()
      .cookie(SessionCookieForge.COOKIE_NAME, adminCookie)
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", appName)
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    // create draft version
    val versionId = given()
      .cookie(SessionCookieForge.COOKIE_NAME, adminCookie)
      .`when`()
      .post("/ui/developer/apps/$appId/versions")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    // app overview page renders with the real app name and the new draft version tile
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, adminCookie)
      .`when`()
      .get("/ui/developer/apps/$appId")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="app-name""""))
      .body(containsString(appName))
      .body(containsString("""data-testid="versions-grid""""))
      .body(containsString("""data-testid="version-tile""""))

    // add an entity with a STRING and a LONG property (exercises PropertyType enum + Property @TemplateData binding)
    val entityId = given()
      .cookie(SessionCookieForge.COOKIE_NAME, adminCookie)
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Widget")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    given()
      .cookie(SessionCookieForge.COOKIE_NAME, adminCookie)
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Title")
      .formParam("type", "STRING")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    given()
      .cookie(SessionCookieForge.COOKIE_NAME, adminCookie)
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Count")
      .formParam("type", "LONG")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    // entity editor page renders with both properties in the properties table
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, adminCookie)
      .`when`()
      .get("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="properties-table""""))
      .body(containsString("""data-testid="property-row""""))
      .body(containsString("Title"))
      .body(containsString("Count"))

    // publish the version
    val publishedVersionId = given()
      .cookie(SessionCookieForge.COOKIE_NAME, adminCookie)
      .contentType("application/x-www-form-urlencoded")
      .formParam("bumpType", "FEATURE")
      .formParam("releaseNotes", "Native test release")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/publish")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    // published version page renders with the real version number and release notes
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, adminCookie)
      .`when`()
      .get("/ui/developer/apps/$appId/versions/$publishedVersionId")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="version-number""""))
      .body(containsString("""data-testid="version-release-notes""""))
      .body(containsString("Native test release"))

    // install the app as the (non-admin) user
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, userCookie)
      .`when`()
      .post("/ui/user/app-store/apps/$appId/install")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    // app store page renders with the published app in the store grid
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, userCookie)
      .`when`()
      .get("/ui/user/app-store")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="store-apps-grid""""))
      .body(containsString("""data-testid="store-app-tile""""))
      .body(containsString(appName))

    // user dashboard renders with the newly installed app tile - extract the installedAppId from it
    val dashboardHtml = given()
      .cookie(SessionCookieForge.COOKIE_NAME, userCookie)
      .`when`()
      .get("/ui/user/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="apps-grid""""))
      .body(containsString("""data-testid="installed-app-tile""""))
      .body(containsString(appName))
      .extract().body().asString()

    val installedAppId = Regex("""href="/ui/user/apps/([^"]+)" class="text-decoration-none" data-testid="installed-app-tile"""")
      .find(dashboardHtml)?.groupValues?.get(1)
    assertTrue(!installedAppId.isNullOrBlank(), "Expected to find an installedAppId on the user dashboard for '$appName'")

    // app detail page renders with the entity tile (no data yet)
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, userCookie)
      .`when`()
      .get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="app-title""""))
      .body(containsString(appName))
      .body(containsString("""data-testid="entity-tile""""))

    // create one real app-data record for the Widget entity
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, userCookie)
      .contentType("application/x-www-form-urlencoded")
      .formParam("entityTypeId", entityId)
      .`when`()
      .post("/ui/user/apps/$installedAppId/data")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    // app detail page now renders the (non-empty) app-data table for the entity
    val appDetailHtml = given()
      .cookie(SessionCookieForge.COOKIE_NAME, userCookie)
      .`when`()
      .get("/ui/user/apps/$installedAppId")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="app-data-table""""))
      .body(containsString("""data-testid="app-data-row""""))
      .extract().body().asString()

    val dataId = Regex("""data-href="/ui/user/apps/$installedAppId/data/([^"]+)"""").find(appDetailHtml)?.groupValues?.get(1)
    assertTrue(!dataId.isNullOrBlank(), "Expected to find a data-href for the created app-data row")

    // app-data detail/edit page renders with the real property values (exercises AppDataDetail/AppDataPropertyView)
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, userCookie)
      .`when`()
      .get("/ui/user/apps/$installedAppId/data/$dataId")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Title"))
      .body(containsString("Count"))
  }
}
