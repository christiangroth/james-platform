package de.chrgroth.james.platform.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Drives the personal access token flow against the packaged native executable: a user creates a token on the profile page (Qute
 * @TemplateData rows), then uses it on the `/api/v1` JSON API - exercising the Bearer auth mechanism, the Jackson serialization of the
 * non-empty ApiInstalledAppDetailDto/ApiVersionDto/ApiAppDataDto graphs and the @JsonCreator request deserialization, which are exactly the
 * reflection paths that only break in native mode (see JsonResponseReflectionConfig, #672). App setup follows DeveloperAndUserFlowIT.
 */
@QuarkusIntegrationTest
class ApiTokenCheckIT {

  @Test
  fun `user creates a token on the profile page and reads and writes app data via the api`() {
    val adminCookie = SessionCookieForge.forgeSessionCookie("admin")
    val appName = "Native Token App ${System.nanoTime()}"
    val testUsername = "native-token-user-${System.nanoTime()}"

    // plain USER-role account for the user-facing half (user routes block the ADMIN role)
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, adminCookie)
      .contentType("application/x-www-form-urlencoded")
      .formParam("password", "native-test-password-1!")
      .`when`()
      .put("/ui/admin/users/$testUsername")
      .then().log().ifValidationFails()
      .statusCode(200)
    val userCookie = SessionCookieForge.forgeSessionCookie(testUsername)

    // developer side (as admin): app with one entity and a STRING property, published
    val appId = given()
      .cookie(SessionCookieForge.COOKIE_NAME, adminCookie)
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", appName)
      .`when`()
      .post("/ui/developer/apps")
      .then().log().ifValidationFails()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")
    val versionId = given()
      .cookie(SessionCookieForge.COOKIE_NAME, adminCookie)
      .`when`()
      .post("/ui/developer/apps/$appId/versions")
      .then().log().ifValidationFails()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")
    val entityId = given()
      .cookie(SessionCookieForge.COOKIE_NAME, adminCookie)
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Widget")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then().log().ifValidationFails()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, adminCookie)
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Title")
      .formParam("type", "STRING")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties")
      .then().log().ifValidationFails()
      .statusCode(200)
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, adminCookie)
      .contentType("application/x-www-form-urlencoded")
      .formParam("bumpType", "FEATURE")
      .formParam("releaseNotes", "Native token test release")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/publish")
      .then().log().ifValidationFails()
      .statusCode(200)
    given()
      .cookie(SessionCookieForge.COOKIE_NAME, userCookie)
      .`when`()
      .post("/ui/user/app-store/apps/$appId/install")
      .then().log().ifValidationFails()
      .statusCode(200)

    // create the token on the profile page, the plain token is rendered exactly once
    val profileHtml = given()
      .cookie(SessionCookieForge.COOKIE_NAME, userCookie)
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "native agent")
      .formParam("scopes", "USER_WRITE")
      .`when`()
      .post("/ui/profile/api-tokens")
      .then().log().ifValidationFails()
      .statusCode(200)
      .body(containsString("""data-testid="api-tokens-table""""))
      .extract().body().asString()
    val token = Regex("""data-testid="api-token-created-value">(jp_[A-Za-z0-9_-]+)<""").find(profileHtml)?.groupValues?.get(1)
    assertNotNull(token, "Expected the created token on the profile page")
    val bearer = "Bearer $token"

    given()
      .header("Authorization", bearer)
      .`when`()
      .get("/api/v1/token")
      .then().log().ifValidationFails()
      .statusCode(200)
      .body("username", equalTo(testUsername))
      .body("scopes", equalTo(listOf("USER_READ", "USER_WRITE")))

    val installedAppId = given()
      .header("Authorization", bearer)
      .`when`()
      .get("/api/v1/user/apps")
      .then().log().ifValidationFails()
      .statusCode(200)
      .body("appId", hasItem(appId))
      .extract().path<String>("find { it.appId == '$appId' }.installedAppId")

    val propertyId = given()
      .header("Authorization", bearer)
      .`when`()
      .get("/api/v1/user/apps/$installedAppId")
      .then().log().ifValidationFails()
      .statusCode(200)
      .body("version.entities[0].name", equalTo("Widget"))
      .extract().path<String>("version.entities[0].properties[0].id")

    val dataId = given()
      .header("Authorization", bearer)
      .contentType("application/json")
      .body("""{"entityTypeId":"$entityId","values":{"$propertyId":"from native api"}}""")
      .`when`()
      .post("/api/v1/user/apps/$installedAppId/data")
      .then().log().ifValidationFails()
      .statusCode(201)
      .extract().path<String>("id")

    given()
      .header("Authorization", bearer)
      .`when`()
      .get("/api/v1/user/apps/$installedAppId/data")
      .then().log().ifValidationFails()
      .statusCode(200)
      .body("id", equalTo(listOf(dataId)))
      .body("[0].values.$propertyId", equalTo("from native api"))

    // the token never reaches the UI, and errors under /api are JSON
    given()
      .header("Authorization", bearer)
      .redirects().follow(false)
      .`when`()
      .get("/ui/profile")
      .then().log().ifValidationFails()
      .statusCode(403)
    given()
      .header("Authorization", bearer)
      .`when`()
      .get("/api/v1/user/apps/unknown-installation")
      .then().log().ifValidationFails()
      .statusCode(404)
      .body("code", equalTo("STORE-005"))
  }
}
