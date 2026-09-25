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
@TestSecurity(user = "test-aggregation-editor-user", roles = ["DEVELOPER"])
class AggregationEditorPageTests {

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @BeforeEach
  fun setup() {
    if (userRepository.findByUsername(Username("test-aggregation-editor-user")) == null) {
      userRepository.save(
        User(
          id = UserId(UUID.randomUUID().toString()),
          username = Username("test-aggregation-editor-user"),
          passwordHash = "test-hash",
          roles = setOf(UserRole.DEVELOPER),
          createdAt = Instant.now(),
        ),
      )
    }
  }

  private data class Draft(val appId: String, val versionId: String, val entityId: String, val amountPropertyId: String, val notePropertyId: String) {
    val entityUrl = "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId"
  }

  private fun setupDraft(): Draft {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Aggregation Editor App ${System.nanoTime()}")
      .`when`()
      .post("/ui/developer/apps")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    val versionId = given()
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
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

    fun addProperty(name: String, type: String): String = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", name)
      .formParam("type", type)
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("propertyId")

    return Draft(appId, versionId, entityId, addProperty("Amount", "LONG"), addProperty("Note", "STRING"))
  }

  private fun postAggregation(url: String, name: String, function: String, sourceProperty: String) = given()
    .contentType("application/x-www-form-urlencoded")
    .formParam("name", name)
    .formParam("function", function)
    .formParam("sourceProperty", sourceProperty)
    .formParam("refPath", "")
    .formParam("timeBucket", "MONAT")
    .formParam("timeProperty", "")
    .formParam("groupBy", "")
    .`when`()
    .post(url)
    .then()
    .statusCode(200)

  private fun aggregationIdOnPage(draft: Draft): String {
    val html = given().`when`().get(draft.entityUrl).then().statusCode(200).extract().body().asString()
    return Regex("""data-aggregation-id="([^"]+)"""").find(html)?.groupValues?.get(1) ?: ""
  }

  @Test
  fun `entity editor shows empty aggregation section and the editor modal for a draft`() {
    val draft = setupDraft()

    given()
      .`when`()
      .get(draft.entityUrl)
      .then()
      .statusCode(200)
      .body(containsString("data-testid=\"no-aggregations\""))
      .body(containsString("data-testid=\"open-add-aggregation-modal-button\""))
      .body(containsString("id=\"aggregationModal\""))
  }

  @Test
  fun `adding an aggregation shows it in the entity editor`() {
    val draft = setupDraft()

    postAggregation("${draft.entityUrl}/aggregations", "Umsatz gesamt", "SUM", draft.amountPropertyId)
      .body(containsString("\"ok\":true"))

    given()
      .`when`()
      .get(draft.entityUrl)
      .then()
      .statusCode(200)
      .body(containsString("data-testid=\"aggregations-table\""))
      .body(containsString("Umsatz gesamt"))
      .body(containsString("pro Monat"))
      .body(not(containsString("data-testid=\"no-aggregations\"")))
  }

  @Test
  fun `adding an invalid aggregation returns the validation message as form feedback`() {
    val draft = setupDraft()

    postAggregation("${draft.entityUrl}/aggregations", "Notizsumme", "SUM", draft.notePropertyId)
      .body(containsString("\"ok\":false"))
      .body(containsString("nicht numerisch"))

    given()
      .`when`()
      .get(draft.entityUrl)
      .then()
      .statusCode(200)
      .body(containsString("data-testid=\"no-aggregations\""))
  }

  @Test
  fun `adding an aggregation with both a reference and groupBy is rejected with a specific message`() {
    val draft = setupDraft()
    val targetEntityId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Customer")
      .`when`()
      .post("/ui/developer/apps/${draft.appId}/versions/${draft.versionId}/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")
    val refPropertyId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Customer")
      .formParam("type", "REF")
      .formParam("nullable", false)
      .formParam("targetEntityId", targetEntityId)
      .`when`()
      .post("${draft.entityUrl}/properties")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("propertyId")

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "Umsatz je Kunde und Notiz")
      .formParam("function", "SUM")
      .formParam("sourceProperty", draft.amountPropertyId)
      .formParam("refPath", refPropertyId)
      .formParam("timeBucket", "")
      .formParam("timeProperty", "")
      .formParam("groupBy", draft.notePropertyId)
      .`when`()
      .post("${draft.entityUrl}/aggregations")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":false"))
      .body(containsString("nicht gleichzeitig gesetzt werden"))

    given()
      .`when`()
      .get(draft.entityUrl)
      .then()
      .statusCode(200)
      .body(containsString("data-testid=\"no-aggregations\""))
      .body(containsString("updateAggregationGroupingOptions"))
  }

  @Test
  fun `adding an aggregation without a name is rejected`() {
    val draft = setupDraft()

    postAggregation("${draft.entityUrl}/aggregations", " ", "COUNT", draft.notePropertyId)
      .body(containsString("\"ok\":false"))
      .body(containsString("Name der Aggregation ist erforderlich"))
  }

  @Test
  fun `updating and deleting an aggregation is reflected in the entity editor`() {
    val draft = setupDraft()
    postAggregation("${draft.entityUrl}/aggregations", "Anzahl Notizen", "COUNT", draft.notePropertyId)
      .body(containsString("\"ok\":true"))
    val aggregationId = aggregationIdOnPage(draft)

    postAggregation("${draft.entityUrl}/aggregations/$aggregationId", "Maximalbetrag", "MAX", draft.amountPropertyId)
      .body(containsString("\"ok\":true"))
    given()
      .`when`()
      .get(draft.entityUrl)
      .then()
      .statusCode(200)
      .body(containsString("Maximalbetrag"))
      .body(not(containsString("Anzahl Notizen")))

    given()
      .`when`()
      .post("${draft.entityUrl}/aggregations/$aggregationId/delete")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))
    given()
      .`when`()
      .get(draft.entityUrl)
      .then()
      .statusCode(200)
      .body(containsString("data-testid=\"no-aggregations\""))
  }

  @Test
  fun `a draft with an aggregation added through the editor can be published`() {
    val draft = setupDraft()
    postAggregation("${draft.entityUrl}/aggregations", "Umsatz gesamt", "SUM", draft.amountPropertyId)
      .body(containsString("\"ok\":true"))

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("bumpType", "BUGFIX")
      .formParam("releaseNotes", "Initial release")
      .`when`()
      .post("/ui/developer/apps/${draft.appId}/versions/publish")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))
  }
}
