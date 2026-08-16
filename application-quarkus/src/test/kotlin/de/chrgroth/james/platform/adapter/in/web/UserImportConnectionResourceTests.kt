package de.chrgroth.james.platform.adapter.`in`.web

import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.out.imports.ImportFetchPort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Instant
import java.util.UUID

@QuarkusTest
@TestSecurity(user = "test-connection-user", roles = ["DATA_IMPORT"])
class UserImportConnectionResourceTests {

  @InjectMock
  lateinit var importFetch: ImportFetchPort

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @BeforeEach
  fun setup() {
    ensureUser("test-connection-user", setOf(UserRole.DATA_IMPORT))
  }

  private fun ensureUser(username: String, roles: Set<UserRole>) {
    if (userRepository.findByUsername(Username(username)) == null) {
      userRepository.save(
        User(
          id = UserId(UUID.randomUUID().toString()),
          username = Username(username),
          passwordHash = "test-hash",
          roles = roles,
          createdAt = Instant.now(),
        ),
      )
    }
  }

  private fun createConnection(
    name: String = "Test Connection ${System.nanoTime()}",
    url: String = "https://example.com/data",
    bearerToken: String? = "secret-token",
  ): String {
    val request = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", name)
      .formParam("baseUrl", url)
    (if (bearerToken != null) request.formParam("bearerToken", bearerToken) else request)
      .`when`()
      .post("/ui/user/imports/connections")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val tableHtml = given()
      .`when`()
      .get("/ui/user/imports/connections/table")
      .then()
      .statusCode(200)
      .extract().body().asString()

    return Regex("""data-connection-id="([^"]+)"\s+data-connection-name="${Regex.escape(name)}"""")
      .find(tableHtml)?.groupValues?.get(1)
      ?: error("Expected connection '$name' in the rendered connections table")
  }

  @Test
  fun `creating a connection with a name and url succeeds and appears in the table with no token stored`() {
    val name = "My Connection ${System.nanoTime()}"

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", name)
      .formParam("baseUrl", "https://example.com/data")
      .`when`()
      .post("/ui/user/imports/connections")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val tableHtml = given()
      .`when`()
      .get("/ui/user/imports/connections/table")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(tableHtml.contains(name), "Expected the new connection's name to be rendered")
    assertTrue(tableHtml.contains("Nicht hinterlegt"), "Expected the token status badge to show no token is stored")
  }

  @Test
  fun `creating a connection with a bearer token stores it without exposing it in the table`() {
    val name = "Token Connection ${System.nanoTime()}"

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", name)
      .formParam("baseUrl", "https://example.com/data")
      .formParam("bearerToken", "super-secret")
      .`when`()
      .post("/ui/user/imports/connections")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val tableHtml = given()
      .`when`()
      .get("/ui/user/imports/connections/table")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(!tableHtml.contains("super-secret"), "Expected the raw bearer token to never be rendered back to the client")
    assertTrue(tableHtml.contains("Hinterlegt"), "Expected the token status badge to show a token is stored")
  }

  @Test
  fun `creating a connection reports an error for a blank name`() {
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "")
      .formParam("baseUrl", "https://example.com/data")
      .`when`()
      .post("/ui/user/imports/connections")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `creating a connection reports an error for a blank url`() {
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "No Url Connection")
      .formParam("baseUrl", "")
      .`when`()
      .post("/ui/user/imports/connections")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `updating a connection replaces its name and url`() {
    val connectionId = createConnection()
    val newName = "Renamed Connection ${System.nanoTime()}"

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", newName)
      .formParam("baseUrl", "https://example.com/v2")
      .`when`()
      .post("/ui/user/imports/connections/$connectionId")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val tableHtml = given()
      .`when`()
      .get("/ui/user/imports/connections/table")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(tableHtml.contains(newName), "Expected the renamed connection to be rendered")
    assertTrue(tableHtml.contains("https://example.com/v2"), "Expected the updated url to be rendered")
  }

  @Test
  fun `updating a connection with clearBearerToken removes the stored token`() {
    val connectionId = createConnection(bearerToken = "secret-token")

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Cleared Token Connection")
      .formParam("baseUrl", "https://example.com/data")
      .formParam("clearBearerToken", "on")
      .`when`()
      .post("/ui/user/imports/connections/$connectionId")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val tableHtml = given()
      .`when`()
      .get("/ui/user/imports/connections/table")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(tableHtml.contains("Nicht hinterlegt"), "Expected the token status badge to show no token after clearing it")
  }

  @Test
  fun `updating an unknown connection reports an error`() {
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Does Not Matter")
      .formParam("baseUrl", "https://example.com/data")
      .`when`()
      .post("/ui/user/imports/connections/unknown-connection")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `deleting a connection removes it from the table`() {
    val connectionId = createConnection()

    given()
      .`when`()
      .post("/ui/user/imports/connections/$connectionId/delete")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val tableHtml = given()
      .`when`()
      .get("/ui/user/imports/connections/table")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(!tableHtml.contains("data-connection-id=\"$connectionId\""), "Expected the deleted connection to no longer appear in the table")
  }

  @Test
  fun `deleting an unknown connection reports an error`() {
    given()
      .`when`()
      .post("/ui/user/imports/connections/unknown-connection/delete")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `testing a connection performs a real fetch and reports success`() {
    val connectionId = createConnection()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"ok":true}""".right())

    given()
      .`when`()
      .post("/ui/user/imports/connections/$connectionId/test")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
  }

  @Test
  fun `testing a connection reports the fetch failure`() {
    val connectionId = createConnection()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn(ImportError.FETCH_FAILED.left())

    given()
      .`when`()
      .post("/ui/user/imports/connections/$connectionId/test")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `testing an unknown connection reports an error`() {
    given()
      .`when`()
      .post("/ui/user/imports/connections/unknown-connection/test")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `connections page renames the URL and token column headers and shows the test button as a gray icon-only button`() {
    createConnection()

    val html = given()
      .`when`()
      .get("/ui/user/imports/connections")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains(">Base URL<"), "Expected the URL column header to be renamed to 'Base URL'")
    assertTrue(html.contains(">Auth<"), "Expected the Bearer-Token column header to be renamed to 'Auth'")
    assertTrue(html.contains("btn-app-secondary btn-sm me-1 test-connection-button"), "Expected the test button to use the gray secondary button style")
    assertTrue(!html.contains(">Testen<"), "Expected the test button to no longer show text, only an icon")
    assertTrue(!html.contains("data-testid=\"edit-connection-button\""), "Expected the explicit edit button to no longer be present")
    assertTrue(html.contains("data-testid=\"connection-row\""), "Expected the connection row to still be rendered")
    assertTrue(html.contains("class=\"app-clickable-row\""), "Expected the connection row to be clickable to open the edit modal")
    assertTrue(html.contains("data-mode=\"edit\""), "Expected the connection row to open the modal in edit mode")
    assertTrue(
      html.contains("data-testid=\"new-connection-button\" data-mode=\"create\">\n                <i class=\"bi bi-plus-lg\" aria-hidden=\"true\"></i>\n            </button>\n        </div>"),
      "Expected the new-connection button to show only an icon, no text",
    )
  }
}
