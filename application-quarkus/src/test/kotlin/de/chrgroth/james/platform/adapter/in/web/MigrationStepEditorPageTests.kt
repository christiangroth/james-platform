package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@QuarkusTest
@TestSecurity(user = "test-migration-step-editor-user", roles = ["DEVELOPER"])
class MigrationStepEditorPageTests {

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @BeforeEach
  fun setup() {
    if (userRepository.findByUsername(Username("test-migration-step-editor-user")) == null) {
      userRepository.save(
        User(
          id = UserId(UUID.randomUUID().toString()),
          username = Username("test-migration-step-editor-user"),
          passwordHash = "test-hash",
          roles = setOf(UserRole.DEVELOPER),
          createdAt = Instant.now(),
        ),
      )
    }
  }

  private data class Draft(
    val appId: String,
    val v2VersionId: String,
    val entityId: String,
    val codePropertyId: String,
    val legacyPropertyId: String,
    val newPropertyId: String,
  ) {
    val entityUrl = "/ui/developer/apps/$appId/versions/$v2VersionId/entities/$entityId"
  }

  private fun addProperty(entityUrl: String, name: String, type: String): String = given()
    .contentType("application/x-www-form-urlencoded")
    .formParam("name", name)
    .formParam("type", type)
    .`when`()
    .post("$entityUrl/properties")
    .then()
    .statusCode(200)
    .extract().body().jsonPath().getString("propertyId")

  /**
   * A Version 1.0.0 with an entity carrying a `STRING` "Code" property and a "Legacy" property, published, followed by a Version 2
   * draft where "Code" was retyped to `LONG` in place and "Legacy" was deleted and replaced by a new "LegacyNew" property - the two
   * scenarios a `ConvertType`/`CopyValue` migration step addresses.
   */
  private fun setupDraft(): Draft {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Migration Step Editor App ${System.nanoTime()}")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    val v1VersionId = given()
      .`when`()
      .post("/ui/developer/apps/$appId/versions")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    val entityId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Order")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$v1VersionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    val v1EntityUrl = "/ui/developer/apps/$appId/versions/$v1VersionId/entities/$entityId"
    val codePropertyId = addProperty(v1EntityUrl, "Code", "STRING")
    val legacyPropertyId = addProperty(v1EntityUrl, "Legacy", "STRING")

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("bumpType", "BUGFIX")
      .formParam("releaseNotes", "Initial release")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/publish")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    val v2VersionId = given()
      .`when`()
      .post("/ui/developer/apps/$appId/versions")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    val v2EntityUrl = "/ui/developer/apps/$appId/versions/$v2VersionId/entities/$entityId"
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Code")
      .formParam("type", "LONG")
      .`when`()
      .post("$v2EntityUrl/properties/$codePropertyId")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    given()
      .contentType("application/x-www-form-urlencoded")
      .`when`()
      .post("$v2EntityUrl/properties/$legacyPropertyId/delete")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))
    val newPropertyId = addProperty(v2EntityUrl, "LegacyNew", "LONG")

    return Draft(appId, v2VersionId, entityId, codePropertyId, legacyPropertyId, newPropertyId)
  }

  private fun migrationStepIdOnPage(draft: Draft): String {
    val html = given().`when`().get(draft.entityUrl).then().statusCode(200).extract().body().asString()
    return Regex("""data-migration-step-id="([^"]+)"""").find(html)?.groupValues?.get(1) ?: ""
  }

  @Test
  fun `entity editor shows empty migration section and the editor modal for a draft`() {
    val draft = setupDraft()

    given()
      .`when`()
      .get(draft.entityUrl)
      .then()
      .statusCode(200)
      .body(containsString("data-testid=\"no-migration-steps\""))
      .body(containsString("data-testid=\"open-add-migration-step-modal-button\""))
      .body(containsString("id=\"migrationStepModal\""))
  }

  @Test
  fun `adding a convert-type migration step shows it in the entity editor`() {
    val draft = setupDraft()

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("type", "CONVERT_TYPE")
      .formParam("propertyId", draft.codePropertyId)
      .`when`()
      .post("${draft.entityUrl}/migration-steps")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    given()
      .`when`()
      .get(draft.entityUrl)
      .then()
      .statusCode(200)
      .body(containsString("data-testid=\"migration-steps-table\""))
      .body(containsString("Typ konvertieren: Code"))
      .body(not(containsString("data-testid=\"no-migration-steps\"")))
  }

  @Test
  fun `adding a copy-value migration step shows it in the entity editor`() {
    val draft = setupDraft()

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("type", "COPY_VALUE")
      .formParam("sourcePropertyId", draft.legacyPropertyId)
      .formParam("targetPropertyId", draft.newPropertyId)
      .`when`()
      .post("${draft.entityUrl}/migration-steps")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    given()
      .`when`()
      .get(draft.entityUrl)
      .then()
      .statusCode(200)
      .body(containsString("Wert übernehmen: Legacy → LegacyNew"))
  }

  @Test
  fun `adding a migration step whose source property has no published baseline is rejected`() {
    val draft = setupDraft()
    val brandNewPropertyId = addProperty(draft.entityUrl, "BrandNew", "STRING")

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("type", "CONVERT_TYPE")
      .formParam("propertyId", brandNewPropertyId)
      .`when`()
      .post("${draft.entityUrl}/migration-steps")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":false"))
      .body(containsString("letzten veröffentlichten Version"))

    given()
      .`when`()
      .get(draft.entityUrl)
      .then()
      .statusCode(200)
      .body(containsString("data-testid=\"no-migration-steps\""))
  }

  @Test
  fun `updating and deleting a migration step is reflected in the entity editor`() {
    val draft = setupDraft()
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("type", "CONVERT_TYPE")
      .formParam("propertyId", draft.codePropertyId)
      .`when`()
      .post("${draft.entityUrl}/migration-steps")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))
    val stepId = migrationStepIdOnPage(draft)

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("type", "COPY_VALUE")
      .formParam("sourcePropertyId", draft.legacyPropertyId)
      .formParam("targetPropertyId", draft.newPropertyId)
      .`when`()
      .post("${draft.entityUrl}/migration-steps/$stepId")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))
    given()
      .`when`()
      .get(draft.entityUrl)
      .then()
      .statusCode(200)
      .body(containsString("Wert übernehmen: Legacy → LegacyNew"))
      .body(not(containsString("Typ konvertieren: Code")))

    given()
      .contentType("application/x-www-form-urlencoded")
      .`when`()
      .post("${draft.entityUrl}/migration-steps/$stepId/delete")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))
    given()
      .`when`()
      .get(draft.entityUrl)
      .then()
      .statusCode(200)
      .body(containsString("data-testid=\"no-migration-steps\""))
  }

  @Test
  fun `a draft with a valid migration step can be published`() {
    val draft = setupDraft()
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("type", "CONVERT_TYPE")
      .formParam("propertyId", draft.codePropertyId)
      .`when`()
      .post("${draft.entityUrl}/migration-steps")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("bumpType", "BUGFIX")
      .formParam("releaseNotes", "Code ist jetzt eine Zahl")
      .`when`()
      .post("/ui/developer/apps/${draft.appId}/versions/publish")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))
  }

  @Test
  fun `publishing with a migration step invalidated by a later property deletion is rejected`() {
    val draft = setupDraft()
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("type", "COPY_VALUE")
      .formParam("sourcePropertyId", draft.legacyPropertyId)
      .formParam("targetPropertyId", draft.newPropertyId)
      .`when`()
      .post("${draft.entityUrl}/migration-steps")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    given()
      .contentType("application/x-www-form-urlencoded")
      .`when`()
      .post("${draft.entityUrl}/properties/${draft.newPropertyId}/delete")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("bumpType", "BUGFIX")
      .formParam("releaseNotes", "Legacy entfernt")
      .`when`()
      .post("/ui/developer/apps/${draft.appId}/versions/publish")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":false"))
      .body(containsString("Ungültige Migrationsbausteine"))
  }
}
