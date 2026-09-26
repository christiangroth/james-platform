package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Covers the Version editor's interactive migration preview (see docs/adr/0023-migration-steps.md), the endpoint counterpart to
 * [MigrationStepEditorPageTests], which only covers the editor CRUD for the steps themselves.
 */
@QuarkusTest
@TestSecurity(user = "test-migration-preview-user", roles = ["DEVELOPER"])
class MigrationPreviewPageTests {

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @Inject
  lateinit var installedAppRepository: InstalledAppRepositoryPort

  @BeforeEach
  fun setup() {
    if (userRepository.findByUsername(Username("test-migration-preview-user")) == null) {
      userRepository.save(
        User(
          id = UserId(UUID.randomUUID().toString()),
          username = Username("test-migration-preview-user"),
          passwordHash = "test-hash",
          roles = setOf(UserRole.DEVELOPER),
          createdAt = Instant.now(),
        ),
      )
    }
  }

  private data class Draft(val appId: String, val v2VersionId: String, val entityId: String, val codePropertyId: String) {
    val entityUrl = "/ui/developer/apps/$appId/versions/$v2VersionId/entities/$entityId"
    val previewSampleUrl = "$entityUrl/migration-preview/sample"
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
   * A Version 1.0.0 with an entity carrying a `STRING` "Code" property, published and installed with one existing AppData object
   * (its "Code" value is [codeValue]), followed by a Version 2 draft where "Code" was retyped to `LONG` in place - the scenario a
   * `ConvertType` migration step addresses. Mirrors [MigrationStepEditorPageTests.setupDraft], plus a real install and one existing
   * AppData object for the preview to run against.
   */
  private fun setupDraftWithSampleData(codeValue: String?): Draft {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Migration Preview App ${System.nanoTime()}")
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

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("bumpType", "BUGFIX")
      .formParam("releaseNotes", "Initial release")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/publish")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    given()
      .`when`()
      .post("/ui/user/app-store/apps/$appId/install")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))
    val installedAppId = installedAppRepository.findAllByAppId(AppId(appId)).single().id.value

    val dataForm = given().contentType("application/x-www-form-urlencoded").formParam("entityTypeId", entityId)
    if (codeValue != null) dataForm.formParam("prop_$codePropertyId", codeValue)
    dataForm.`when`().post("/ui/user/apps/$installedAppId/data").then().statusCode(200).body(containsString("\"ok\":true"))

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

    return Draft(appId, v2VersionId, entityId, codePropertyId)
  }

  private fun addConvertTypeStep(draft: Draft) {
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("type", "CONVERT_TYPE")
      .formParam("propertyId", draft.codePropertyId)
      .`when`()
      .post("${draft.entityUrl}/migration-steps")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))
  }

  @Test
  fun `migration preview sample shows the before and after value once a matching migration step exists`() {
    val draft = setupDraftWithSampleData(codeValue = "42")
    addConvertTypeStep(draft)

    given()
      .`when`()
      .get("${draft.previewSampleUrl}?index=0")
      .then()
      .statusCode(200)
      .body("total", equalTo(1))
      .body("sample.isValid", equalTo(true))
      .body("sample.properties[0].name", equalTo("Code"))
      .body("sample.properties[0].before", equalTo("42"))
      .body("sample.properties[0].after", equalTo("42"))
      .body("sample.properties[0].hasIssue", equalTo(false))
  }

  @Test
  fun `migration preview sample reports a step issue without aborting when the existing value cannot be converted`() {
    val draft = setupDraftWithSampleData(codeValue = "not-a-number")
    addConvertTypeStep(draft)

    given()
      .`when`()
      .get("${draft.previewSampleUrl}?index=0")
      .then()
      .statusCode(200)
      .body("total", equalTo(1))
      .body("sample.isValid", equalTo(false))
      .body("sample.properties[0].before", equalTo("not-a-number"))
      .body("sample.properties[0].after", equalTo("not-a-number"))
      .body("sample.properties[0].hasIssue", equalTo(true))
  }

  @Test
  fun `migration preview sample reports a constraint violation even without any migration step, from re-validation alone`() {
    val draft = setupDraftWithSampleData(codeValue = "50")
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("minLong", "100")
      .`when`()
      .post("${draft.entityUrl}/properties/${draft.codePropertyId}/constraints")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    given()
      .`when`()
      .get("${draft.previewSampleUrl}?index=0")
      .then()
      .statusCode(200)
      .body("total", equalTo(1))
      .body("sample.isValid", equalTo(false))
      .body("sample.properties[0].before", equalTo("50"))
      .body("sample.properties[0].after", equalTo("50"))
      .body("sample.properties[0].hasIssue", equalTo(true))
  }

  @Test
  fun `migration preview sample returns a null sample for an index beyond the sampled object set`() {
    val draft = setupDraftWithSampleData(codeValue = "42")

    given()
      .`when`()
      .get("${draft.previewSampleUrl}?index=5")
      .then()
      .statusCode(200)
      .body("total", equalTo(1))
      .body("sample", equalTo(null))
  }

  @Test
  fun `migration preview sample returns a zero total for an unknown entity`() {
    val draft = setupDraftWithSampleData(codeValue = "42")

    given()
      .`when`()
      .get("${draft.entityUrl.substringBeforeLast("/")}/unknown-entity/migration-preview/sample?index=0")
      .then()
      .statusCode(200)
      .body("total", equalTo(0))
      .body("sample", equalTo(null))
  }
}
