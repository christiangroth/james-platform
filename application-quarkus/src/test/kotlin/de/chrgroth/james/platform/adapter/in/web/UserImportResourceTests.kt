package de.chrgroth.james.platform.adapter.`in`.web

import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.error.ImportFetchFailedError
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportFetchPort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Instant
import java.util.UUID

@QuarkusTest
@TestSecurity(user = "test-import-trigger-user", roles = ["DEVELOPER", "DATA_IMPORT"])
class UserImportResourceTests {

  @InjectMock
  lateinit var importFetch: ImportFetchPort

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @Inject
  lateinit var appDataRepository: AppDataRepositoryPort

  @BeforeEach
  fun setup() {
    if (userRepository.findByUsername(Username("test-import-trigger-user")) == null) {
      userRepository.save(
        User(
          id = UserId(UUID.randomUUID().toString()),
          username = Username("test-import-trigger-user"),
          passwordHash = "test-hash",
          roles = setOf(UserRole.DEVELOPER, UserRole.DATA_IMPORT),
          createdAt = Instant.now(),
        ),
      )
    }
  }

  /**
   * Accepting a dry run now only enqueues an outbox event (see ADR 0019) instead of running synchronously, so the
   * import job's deletion - the last step of `ImportService.handle` - is observed by polling instead of asserted
   * immediately after the accept call returns. The outbox worker starts at application startup and is signalled
   * on enqueue, so it picks up the task well within this bound under normal test conditions.
   */
  private fun awaitImportAccepted(importJobId: String) {
    val deadlineMs = System.currentTimeMillis() + 5000
    while (System.currentTimeMillis() < deadlineMs) {
      val tableHtml = given().`when`().get("/ui/user/imports/table").then().statusCode(200).extract().body().asString()
      if (!tableHtml.contains("data-import-id=\"$importJobId\"")) return
      Thread.sleep(50)
    }
    throw AssertionError("Expected import job $importJobId to be accepted (and deleted) by the outbox dispatcher within 5s")
  }

  private fun createApp(appName: String): Pair<String, String> {
    val appId = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", appName)
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

    return appId to versionId
  }

  private fun addEntity(appId: String, versionId: String, name: String): String =
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", name)
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("redirectUrl")
      .substringAfterLast("/")

  private fun publishAndInstall(appId: String, appName: String): String {
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("bumpType", "BUGFIX")
      .formParam("releaseNotes", "Initial release")
      .`when`()
      .post("/ui/developer/apps/$appId/versions/publish")
      .then()
      .statusCode(200)

    given()
      .`when`()
      .post("/ui/user/app-store/apps/$appId/install")
      .then()
      .statusCode(200)

    val dashboardHtml = given()
      .`when`()
      .get("/ui/user/dashboard")
      .then()
      .statusCode(200)
      .extract().body().asString()

    return Regex("""href="/ui/user/apps/([^"]+)"[^>]*aria-label="App ${Regex.escape(appName)} öffnen"""")
      .find(dashboardHtml)?.groupValues?.get(1) ?: ""
  }

  private data class InstalledAppWithEntity(val installedAppId: String, val entityId: String, val propertyId: String)

  /** Installs an app with a single entity ("Entity One") that has no properties, returning (installedAppId, entityId). */
  private fun installApp(): Pair<String, String> {
    val appName = "Import Resource App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val entityId = addEntity(appId, versionId, "Entity One")
    val installedAppId = publishAndInstall(appId, appName)
    return installedAppId to entityId
  }

  private fun addProperty(appId: String, versionId: String, entityId: String, name: String, type: String, nullable: Boolean): String =
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", name)
      .formParam("type", type)
      .formParam("nullable", nullable)
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("propertyId")

  private fun addUniqueKeyConstraint(appId: String, versionId: String, entityId: String, propertyId: String) {
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("uniqueKey", true)
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties/$propertyId/constraints")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
  }

  private fun addReferenceProperty(appId: String, versionId: String, entityId: String, name: String, targetEntityId: String): String =
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", name)
      .formParam("type", "REF")
      .formParam("nullable", false)
      .formParam("targetEntityId", targetEntityId)
      .`when`()
      .post("/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties")
      .then()
      .statusCode(200)
      .extract().body().jsonPath().getString("propertyId")

  private fun installAppWithMandatoryStringProperty(): InstalledAppWithEntity {
    val appName = "Import Mapping App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val entityId = addEntity(appId, versionId, "Contact")
    val propertyId = addProperty(appId, versionId, entityId, "Name", "STRING", nullable = false)
    val installedAppId = publishAndInstall(appId, appName)
    return InstalledAppWithEntity(installedAppId, entityId, propertyId)
  }

  /** Creates a connection with a unique [name] and returns its id, resolved from the rendered connections table. */
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

  private fun triggerImport(installedAppId: String, connectionId: String, entityId: String, urlPostfix: String? = null) {
    val request = given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("installedAppId", installedAppId)
      .formParam("connectionId", connectionId)
      .formParam("targetEntityDefinitionId", entityId)
    (if (urlPostfix != null) request.formParam("urlPostfix", urlPostfix) else request)
      .`when`()
      .post("/ui/user/imports")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
  }

  private fun triggerImportWithSingleDataPath(installedAppId: String, entityId: String) {
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"items":[{"name":"Alice"},{"name":"Bob"}]}""".right())
    triggerImport(installedAppId, createConnection(), entityId)
  }

  @Test
  fun `trigger import creates a downloaded job and appears in the table`() {
    val (installedAppId, entityId) = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"foo":"bar"}""".right())

    triggerImport(installedAppId, createConnection(), entityId)

    val tableHtml = given()
      .`when`()
      .get("/ui/user/imports/table")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(tableHtml.contains("data-testid=\"imports-table\""), "Expected the imports table to be rendered")
    assertTrue(tableHtml.contains("data-testid=\"import-status\""), "Expected a status cell for the created import job")
  }

  @Test
  fun `trigger import with a url postfix fetches from the connection's base URL with it appended and shows the resolved URL on the job overview`() {
    val (installedAppId, entityId) = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"foo":"bar"}""".right())

    triggerImport(installedAppId, createConnection(url = "https://example.com/data"), entityId, urlPostfix = "/latest")
    val importId = triggerImportAndGetId(installedAppId)

    val html = given()
      .`when`()
      .get("/ui/user/imports/$importId/overview")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"import-job-source-url\">https://example.com/data/latest<"), "Expected the resolved base URL + postfix to be rendered on the job overview")
  }

  @Test
  fun `trigger import reports an error when the response is not a JSON object`() {
    val (installedAppId, entityId) = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("[1,2,3]".right())

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("installedAppId", installedAppId)
      .formParam("connectionId", createConnection())
      .formParam("targetEntityDefinitionId", entityId)
      .`when`()
      .post("/ui/user/imports")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `trigger import reports an error when no connection is selected`() {
    val (installedAppId, entityId) = installApp()

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("installedAppId", installedAppId)
      .formParam("connectionId", "")
      .formParam("targetEntityDefinitionId", entityId)
      .`when`()
      .post("/ui/user/imports")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `trigger import reports an error when the connection does not exist`() {
    val (installedAppId, entityId) = installApp()

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("installedAppId", installedAppId)
      .formParam("connectionId", "unknown-connection")
      .formParam("targetEntityDefinitionId", entityId)
      .`when`()
      .post("/ui/user/imports")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `trigger import reports an error when the fetch fails`() {
    val (installedAppId, entityId) = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn(ImportError.FETCH_FAILED.left())

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("installedAppId", installedAppId)
      .formParam("connectionId", createConnection())
      .formParam("targetEntityDefinitionId", entityId)
      .`when`()
      .post("/ui/user/imports")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `trigger import reports the technical fetch failure detail`() {
    val (installedAppId, entityId) = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString()))
      .thenReturn(ImportFetchFailedError("Server responded with HTTP status 503.").left())

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("installedAppId", installedAppId)
      .formParam("connectionId", createConnection())
      .formParam("targetEntityDefinitionId", entityId)
      .`when`()
      .post("/ui/user/imports")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
      .body("errorDetails[0]", equalTo("Server responded with HTTP status 503."))
  }

  @Test
  fun `delete import job removes it from the table and the job overview redirects away`() {
    val (installedAppId, entityId) = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"foo":"bar"}""".right())

    triggerImport(installedAppId, createConnection(), entityId)
    val importId = triggerImportAndGetId(installedAppId)

    given()
      .`when`()
      .post("/ui/user/imports/$importId/delete")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val afterDeleteHtml = given()
      .`when`()
      .get("/ui/user/imports/table")
      .then()
      .statusCode(200)
      .extract().body().asString()
    assertTrue(!afterDeleteHtml.contains("data-import-id=\"$importId\""), "Expected the deleted import job to no longer appear in the table")

    given()
      .redirects().follow(false)
      .`when`()
      .get("/ui/user/imports/$importId")
      .then()
      .statusCode(303)
  }

  @Test
  fun `delete import job reports an error for an unknown job id`() {
    val (installedAppId, _) = installApp()

    given()
      .`when`()
      .post("/ui/user/imports/unknown-id/delete")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  private fun triggerImportAndGetId(installedAppId: String): String {
    val tableHtml = given()
      .`when`()
      .get("/ui/user/imports/table")
      .then()
      .statusCode(200)
      .extract().body().asString()
    return Regex("""data-import-id="([^"]+)"""").find(tableHtml)?.groupValues?.get(1)
      ?: error("Expected an import id in the rendered table")
  }

  @Test
  fun `trigger import auto-selects the single detected data path`() {
    val (installedAppId, entityId) = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"items":[{"a":1},{"a":2}]}""".right())

    triggerImport(installedAppId, createConnection(), entityId)
    val importId = triggerImportAndGetId(installedAppId)

    val jobHtml = given()
      .`when`()
      .get("/ui/user/imports/$importId/overview")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(jobHtml.contains("data-testid=\"selected-data-path\">"), "Expected the auto-selected data path to be rendered")
    assertTrue(jobHtml.contains("/items"), "Expected the data path to be rendered in leading-slash form")
  }

  @Test
  fun `select data path reports an error for a path that is not an array of objects`() {
    val (installedAppId, entityId) = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"foo":"bar"}""".right())

    triggerImport(installedAppId, createConnection(), entityId)
    val importId = triggerImportAndGetId(installedAppId)

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("dataPath", "foo")
      .`when`()
      .post("/ui/user/imports/$importId/select-path")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `select data path succeeds and updates the job overview`() {
    val (installedAppId, entityId) = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"a":[{"x":1}],"b":[{"y":1},{"y":2}]}""".right())

    triggerImport(installedAppId, createConnection(), entityId)
    val importId = triggerImportAndGetId(installedAppId)

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("dataPath", "/b")
      .`when`()
      .post("/ui/user/imports/$importId/select-path")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val jobHtml = given()
      .`when`()
      .get("/ui/user/imports/$importId/overview")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(jobHtml.contains("/b"), "Expected the manually selected data path (submitted in leading-slash form) to be rendered the same way")
  }

  @Test
  fun `select data path reports an error for a blank path`() {
    val (installedAppId, entityId) = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"foo":"bar"}""".right())

    triggerImport(installedAppId, createConnection(), entityId)
    val importId = triggerImportAndGetId(installedAppId)

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("dataPath", "")
      .`when`()
      .post("/ui/user/imports/$importId/select-path")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `select data path reports an error for an unknown job id`() {
    val (installedAppId, _) = installApp()

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("dataPath", "items")
      .`when`()
      .post("/ui/user/imports/unknown-id/select-path")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `mapping page renders the fixed target entity's properties`() {
    val app = installAppWithMandatoryStringProperty()
    triggerImportWithSingleDataPath(app.installedAppId, app.entityId)
    val importId = triggerImportAndGetId(app.installedAppId)

    val html = given()
      .`when`()
      .get("/ui/user/imports/$importId/mapping")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"mapping-property-row\""), "Expected the target entity's property row to be rendered")
    assertTrue(html.contains("data-property-id=\"${app.propertyId}\""), "Expected the property row to reference the created property")
    assertTrue(html.contains("data-testid=\"breadcrumb-import-name\">Contact<"), "Expected the target entity's name to be part of the breadcrumbs")
  }

  @Test
  fun `mapping sample endpoint resolves the mapped preview for the record at the given index, computed from the request's not-yet-saved field mappings`() {
    val app = installAppWithMandatoryStringProperty()
    triggerImportWithSingleDataPath(app.installedAppId, app.entityId)
    val importId = triggerImportAndGetId(app.installedAppId)

    given()
      .contentType("application/json")
      .body("""{"fieldMappings": [{ "targetPropertyId": "${app.propertyId}", "sourcePath": "name", "conversion": "NONE", "fallbackValue": null }]}""")
      .`when`()
      .post("/ui/user/imports/$importId/mapping/sample?index=1")
      .then()
      .statusCode(200)
      .body("total", equalTo(2))
      .body("sample.sourceDataJson", containsString("Bob"))
      .body("sample.properties[0].value", equalTo("Bob"))
      .body("sample.properties[0].hasIssue", equalTo(false))
  }

  @Test
  fun `mapping sample endpoint reports a validation issue when the field mapping leaves a mandatory property unmapped`() {
    val app = installAppWithMandatoryStringProperty()
    triggerImportWithSingleDataPath(app.installedAppId, app.entityId)
    val importId = triggerImportAndGetId(app.installedAppId)

    given()
      .contentType("application/json")
      .body("""{"fieldMappings": []}""")
      .`when`()
      .post("/ui/user/imports/$importId/mapping/sample?index=0")
      .then()
      .statusCode(200)
      .body("sample.properties[0].hasIssue", equalTo(true))
      .body("sample.properties[0].issues[0].message", containsString("Pflichtfeld"))
  }

  @Test
  fun `mapping sample endpoint reports a null sample for an index beyond the record set's size`() {
    val app = installAppWithMandatoryStringProperty()
    triggerImportWithSingleDataPath(app.installedAppId, app.entityId)
    val importId = triggerImportAndGetId(app.installedAppId)

    given()
      .contentType("application/json")
      .body("""{"fieldMappings": []}""")
      .`when`()
      .post("/ui/user/imports/$importId/mapping/sample?index=5")
      .then()
      .statusCode(200)
      .body("total", equalTo(2))
      .body("sample", equalTo(null))
  }

  @Test
  fun `mapping sample endpoint reports a zero total for an unknown import job id`() {
    given()
      .contentType("application/json")
      .body("""{"fieldMappings": []}""")
      .`when`()
      .post("/ui/user/imports/unknown-id/mapping/sample?index=0")
      .then()
      .statusCode(200)
      .body("total", equalTo(0))
      .body("sample", equalTo(null))
  }

  @Test
  fun `filter page lists the detected schema fields and shows the unfiltered record count when no rules are configured yet`() {
    val app = installAppWithMandatoryStringProperty()
    triggerImportWithSingleDataPath(app.installedAppId, app.entityId)
    val importId = triggerImportAndGetId(app.installedAppId)

    val html = given()
      .`when`()
      .get("/ui/user/imports/$importId/filter")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"filter-rules-table\""), "Expected the filter rules table to be rendered")
    assertTrue(html.contains("data-testid=\"filter-no-rules-hint\""), "Expected the empty-state hint when no filter rules are configured yet")
    assertTrue(html.contains("2 von 2 Datensätzen"), "Expected the preview to report both records matching with no filter configured")
    assertTrue(html.contains("data-testid=\"import-step-filter\""), "Expected the step navigator to include the Filter step")
    assertTrue(html.contains("data-type=\"STRING\""), "Expected the detected string field option to carry its schema type for the operator-filtering script")
    assertTrue(html.contains("value=\"GREATER_THAN\" "), "Expected the numeric comparison operator to be offered")
    assertTrue(html.contains("value=\"MATCHES\" "), "Expected the regex operator to be offered")
    assertTrue(
      html.contains("value=\"GREATER_THAN\" data-requires-value=\"true\" data-applicable-types=\"LONG,DOUBLE,DATE,DATETIME\""),
      "Expected the comparison operator to be restricted to the schema types it applies to",
    )
    assertTrue(html.contains("data-testid=\"filter-sample-trigger\""), "Expected the 'view matches' accordion trigger to be rendered")
    assertTrue(html.contains("data-testid=\"filter-sample-panel\""), "Expected the 'view matches' accordion panel to be rendered")
  }

  @Test
  fun `filter sample endpoint resolves a matched record and the total matched count`() {
    val app = installAppWithMandatoryStringProperty()
    triggerImportWithSingleDataPath(app.installedAppId, app.entityId)
    val importId = triggerImportAndGetId(app.installedAppId)
    saveFilterRule(importId, """{"mode": "INCLUDE", "sourcePath": "name", "operator": "EQUALS", "value": "Alice"}""")

    given()
      .`when`()
      .get("/ui/user/imports/$importId/filter/sample?matched=true&index=0")
      .then()
      .statusCode(200)
      .body("total", equalTo(1))
      .body("record.name", equalTo("Alice"))
  }

  @Test
  fun `filter sample endpoint resolves an excluded record and the total excluded count`() {
    val app = installAppWithMandatoryStringProperty()
    triggerImportWithSingleDataPath(app.installedAppId, app.entityId)
    val importId = triggerImportAndGetId(app.installedAppId)
    saveFilterRule(importId, """{"mode": "INCLUDE", "sourcePath": "name", "operator": "EQUALS", "value": "Alice"}""")

    given()
      .`when`()
      .get("/ui/user/imports/$importId/filter/sample?matched=false&index=0")
      .then()
      .statusCode(200)
      .body("total", equalTo(1))
      .body("record.name", equalTo("Bob"))
  }

  @Test
  fun `filter sample endpoint reports a null record for an index beyond the matched side's size`() {
    val app = installAppWithMandatoryStringProperty()
    triggerImportWithSingleDataPath(app.installedAppId, app.entityId)
    val importId = triggerImportAndGetId(app.installedAppId)

    given()
      .`when`()
      .get("/ui/user/imports/$importId/filter/sample?matched=true&index=5")
      .then()
      .statusCode(200)
      .body("total", equalTo(2))
      .body("record", equalTo(null))
  }

  @Test
  fun `filter sample endpoint reports a null record and a zero total when no records match`() {
    val app = installAppWithMandatoryStringProperty()
    triggerImportWithSingleDataPath(app.installedAppId, app.entityId)
    val importId = triggerImportAndGetId(app.installedAppId)
    saveFilterRule(importId, """{"mode": "INCLUDE", "sourcePath": "name", "operator": "EQUALS", "value": "Charlie"}""")

    given()
      .`when`()
      .get("/ui/user/imports/$importId/filter/sample?matched=true&index=0")
      .then()
      .statusCode(200)
      .body("total", equalTo(0))
      .body("record", equalTo(null))
  }

  @Test
  fun `filter sample endpoint reports a zero total for an unknown import job id`() {
    given()
      .`when`()
      .get("/ui/user/imports/unknown-id/filter/sample?matched=true&index=0")
      .then()
      .statusCode(200)
      .body("total", equalTo(0))
      .body("record", equalTo(null))
  }

  /** Saves a single filter [rule] (a JSON object literal, e.g. `{"mode": "INCLUDE", ...}`) for [importId]. */
  private fun saveFilterRule(importId: String, rule: String) {
    given()
      .contentType("application/json")
      .body("""{"rules": [$rule]}""")
      .`when`()
      .post("/ui/user/imports/$importId/filter")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
  }

  @Test
  fun `filter values endpoint resolves the distinct values seen for a source field`() {
    val app = installAppWithMandatoryStringProperty()
    triggerImportWithSingleDataPath(app.installedAppId, app.entityId)
    val importId = triggerImportAndGetId(app.installedAppId)

    given()
      .`when`()
      .get("/ui/user/imports/$importId/filter/values?sourcePath=name")
      .then()
      .statusCode(200)
      .body("", equalTo(listOf("Alice", "Bob")))
  }

  @Test
  fun `saving a filter rule narrows the records that reach mapping and dry-run`() {
    val app = installAppWithMandatoryStringProperty()
    triggerImportWithSingleDataPath(app.installedAppId, app.entityId)
    val importId = triggerImportAndGetId(app.installedAppId)

    given()
      .contentType("application/json")
      .body("""{"rules": [{"mode": "INCLUDE", "sourcePath": "name", "operator": "EQUALS", "value": "Alice"}]}""")
      .`when`()
      .post("/ui/user/imports/$importId/filter")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    given()
      .contentType("application/json")
      .body(
        """{"fieldMappings": [{ "targetPropertyId": "${app.propertyId}", "sourcePath": "name", "conversion": "NONE", "fallbackValue": null }]}""",
      )
      .`when`()
      .post("/ui/user/imports/$importId/mapping")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val html = given()
      .`when`()
      .get("/ui/user/imports/$importId/dry-run")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"total-count\">1<"), "Expected only the filtered-in record to reach the dry-run")
  }

  @Test
  fun `filter step becomes reachable once a data path is selected, before the mapping is configured`() {
    val app = installAppWithMandatoryStringProperty()
    triggerImportWithSingleDataPath(app.installedAppId, app.entityId)
    val importId = triggerImportAndGetId(app.installedAppId)

    val html = given()
      .`when`()
      .get("/ui/user/imports/$importId/overview")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(
      html.contains("<a href=\"/ui/user/imports/$importId/filter\""),
      "Expected the Filter step to already be a navigable link once the data path is identified",
    )
  }

  @Test
  fun `imports list page is top-level, lists the target app installation and links to the job overview page`() {
    val (installedAppId, entityId) = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"foo":"bar"}""".right())
    triggerImport(installedAppId, createConnection(), entityId)
    val importId = triggerImportAndGetId(installedAppId)

    val html = given()
      .`when`()
      .get("/ui/user/imports")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"breadcrumb-imports\""), "Expected a top-level, non-app-scoped Imports breadcrumb")
    assertTrue(!html.contains("data-testid=\"breadcrumb-app\""), "Expected the imports list to no longer be scoped under a single app in the breadcrumbs")
    assertTrue(html.contains("data-testid=\"import-installed-app-name\""), "Expected the target app installation column to be rendered")
    assertTrue(
      html.contains("class=\"import-row\" data-testid=\"import-row\" data-import-id=\"$importId\""),
      "Expected the whole row to be clickable and open the new job overview page, without a separate entity link",
    )
    assertTrue(html.contains("data-testid=\"import-app-select\""), "Expected the New Import modal to offer an installed app selector now that the list is cross-app")
    assertTrue(html.contains("data-testid=\"import-target-entity-select\""), "Expected the New Import modal to offer a target entity selector for the chosen app")
  }

  @Test
  fun `imports table is compact, shows the connection name and leaves the status and actions column headers blank`() {
    val (installedAppId, entityId) = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"foo":"bar"}""".right())
    val connectionName = "Compact Table Connection ${System.nanoTime()}"
    triggerImport(installedAppId, createConnection(name = connectionName), entityId)

    val html = given()
      .`when`()
      .get("/ui/user/imports")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"import-connection-name\">") && html.contains(connectionName), "Expected the connection's name to be rendered in its own column")
    assertTrue(html.contains(">App<"), "Expected the target app installation column header to be shortened to 'App'")
    assertTrue(html.contains(">Entität<"), "Expected the target entity column header to be shortened to 'Entität'")
    assertTrue(html.contains(">Angelegt<"), "Expected the created-at column header to be shortened to 'Angelegt'")
    assertTrue(html.contains(">Aktualisiert<"), "Expected the last-action column header to be renamed to 'Aktualisiert'")
    assertTrue(html.contains("<th></th>"), "Expected the status column header to be blank")
    assertTrue(html.contains("<th class=\"text-end\"></th>"), "Expected the actions column header to be blank")
  }

  @Test
  fun `import job overview page shows a step navigator that unlocks mapping and dry-run as the job progresses`() {
    val app = installAppWithMandatoryStringProperty()
    triggerImportWithSingleDataPath(app.installedAppId, app.entityId)
    val importId = triggerImportAndGetId(app.installedAppId)

    val htmlBeforeMapping = given()
      .`when`()
      .get("/ui/user/imports/$importId/overview")
      .then()
      .statusCode(200)
      .extract().body().asString()
    assertTrue(htmlBeforeMapping.contains("data-testid=\"import-steps\""), "Expected the step navigator to be rendered on the job overview page")
    assertTrue(
      htmlBeforeMapping.contains("app-wizard-step-disabled\" aria-disabled=\"true\" data-testid=\"import-step-dry-run\""),
      "Expected the Dry-Run step to be disabled before the mapping is complete",
    )

    given()
      .contentType("application/json")
      .body(
        """{"fieldMappings": [{ "targetPropertyId": "${app.propertyId}", "sourcePath": "name", "conversion": "NONE", "fallbackValue": null }]}""",
      )
      .`when`()
      .post("/ui/user/imports/$importId/mapping")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val htmlAfterMapping = given()
      .`when`()
      .get("/ui/user/imports/$importId/overview")
      .then()
      .statusCode(200)
      .extract().body().asString()
    assertTrue(
      htmlAfterMapping.contains("<a href=\"/ui/user/imports/$importId/dry-run\""),
      "Expected the Dry-Run step to become a navigable link once the import job is READY",
    )
  }

  @Test
  fun `saving a complete and valid mapping transitions the import job to READY`() {
    val app = installAppWithMandatoryStringProperty()
    triggerImportWithSingleDataPath(app.installedAppId, app.entityId)
    val importId = triggerImportAndGetId(app.installedAppId)

    given()
      .contentType("application/json")
      .body(
        """
        {
          "fieldMappings": [
            { "targetPropertyId": "${app.propertyId}", "sourcePath": "name", "conversion": "NONE", "fallbackValue": null }
          ]
        }
        """.trimIndent(),
      )
      .`when`()
      .post("/ui/user/imports/$importId/mapping")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val html = given()
      .`when`()
      .get("/ui/user/imports/$importId/mapping")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(
      html.contains("<a href=\"/ui/user/imports/$importId/dry-run\""),
      "Expected the import job to be READY (Dry-Run step reachable) after a valid, complete mapping",
    )
  }

  @Test
  fun `saving an incomplete mapping keeps the import job at DATA_IDENTIFIED but still unlocks the Dry-Run step`() {
    val app = installAppWithMandatoryStringProperty()
    triggerImportWithSingleDataPath(app.installedAppId, app.entityId)
    val importId = triggerImportAndGetId(app.installedAppId)

    given()
      .contentType("application/json")
      .body("""{"fieldMappings": []}""")
      .`when`()
      .post("/ui/user/imports/$importId/mapping")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val mappingHtml = given()
      .`when`()
      .get("/ui/user/imports/$importId/mapping")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(
      mappingHtml.contains("<a href=\"/ui/user/imports/$importId/dry-run\""),
      "Expected the Dry-Run step to be reachable so mapping issues can be debugged against the actual source records, even with an incomplete mapping",
    )
    assertTrue(mappingHtml.contains("data-testid=\"mapping-issue\""), "Expected a validation issue to be rendered for the unmapped mandatory field")

    val dryRunHtml = given()
      .`when`()
      .get("/ui/user/imports/$importId/dry-run")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(dryRunHtml.contains("data-testid=\"invalid-count\">2<"), "Expected both records to be reported as invalid since the mandatory field is unmapped")
    assertTrue(dryRunHtml.contains("data-testid=\"dry-run-source-data\""), "Expected the invalid records' source data to be shown alongside the issue for debugging")
  }

  private fun readyImportWithOneValidAndOneInvalidRecord(): Pair<InstalledAppWithEntity, String> {
    val app = installAppWithMandatoryStringProperty()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString()))
      .thenReturn("""{"items":[{"name":"Alice"},{"name":null}]}""".right())
    triggerImport(app.installedAppId, createConnection(), app.entityId)
    val importId = triggerImportAndGetId(app.installedAppId)

    given()
      .contentType("application/json")
      .body(
        """
        {
          "fieldMappings": [
            { "targetPropertyId": "${app.propertyId}", "sourcePath": "name", "conversion": "NONE", "fallbackValue": null }
          ]
        }
        """.trimIndent(),
      )
      .`when`()
      .post("/ui/user/imports/$importId/mapping")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    return app to importId
  }

  @Test
  fun `dry run page reports one valid and one invalid object with the missing mandatory value highlighted`() {
    val (app, importId) = readyImportWithOneValidAndOneInvalidRecord()

    val html = given()
      .`when`()
      .get("/ui/user/imports/$importId/dry-run")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"valid-count\">1<"), "Expected exactly one valid object")
    assertTrue(html.contains("data-testid=\"invalid-count\">1<"), "Expected exactly one invalid object")
    assertTrue(html.contains("data-testid=\"dry-run-object\""), "Expected the invalid object to be rendered")
    assertTrue(html.contains("id=\"invalid-details\" data-bs-parent=\"#dryRunDetailsAccordion\" data-testid=\"invalid-details\""), "Expected the invalid-details panel markup")
    assertTrue(
      html.contains("class=\"collapse show mb-4\" id=\"invalid-details\""),
      "Expected the invalid objects' details to be opened automatically, without needing to click the invalid count first",
    )
    assertTrue(html.contains("data-testid=\"dry-run-issue\""), "Expected the missing mandatory value issue to be rendered")
    assertTrue(html.contains("data-testid=\"statically-checked-badge\""), "Expected the missing mandatory value issue to be flagged as already checked statically")
    assertTrue(html.contains("data-testid=\"breadcrumb-import-name\">Contact<"), "Expected the target entity's name to be part of the breadcrumbs")
    assertTrue(html.contains("data-testid=\"invalid-count-toggle\""), "Expected the invalid count card to be a clickable toggle for its details")
    assertTrue(
      html.indexOf("data-testid=\"replace-dry-run-button\"") < html.indexOf("data-testid=\"total-count\"") &&
        html.indexOf("data-testid=\"add-dry-run-button\"") < html.indexOf("data-testid=\"total-count\""),
      "Expected both accept actions to be rendered above the count cards, reachable without scrolling past the fan-in noise",
    )
    assertTrue(
      html.indexOf("data-testid=\"total-count\"") < html.indexOf("data-testid=\"invalid-objects-heading\""),
      "Expected the problem list to be rendered below the counts in the markup, even though it is now expanded by default",
    )
    assertTrue(html.contains("data-testid=\"valid-objects-table\""), "Expected valid objects to be shown as a reviewable table of target objects instead of a per-object accordion")
    assertTrue(html.contains("data-testid=\"dry-run-valid-object-row\""), "Expected one row per valid object in the valid objects table")
    assertTrue(html.contains("data-testid=\"dry-run-valid-property-value\">Alice<"), "Expected the valid object's target property value to be shown for review")
  }

  @Test
  fun `accepting a dry run saves the valid object, discards the invalid one and deletes the import job`() {
    val (_, importId) = readyImportWithOneValidAndOneInvalidRecord()

    given()
      .`when`()
      .post("/ui/user/imports/$importId/dry-run/accept")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
      .body("redirectUrl", equalTo("/ui/user/imports"))

    awaitImportAccepted(importId)
  }

  /**
   * Covers both REF resolution paths (lookup and direct mapping) in one scenario, since the per-case business logic
   * (which value a lookup resolves to, why a direct mapping is only checked at dry-run) is already exhaustively unit-tested
   * in DryRunExecutorTests; this only needs to prove the REST-layer wiring for both JSON field mapping shapes.
   */
  @Test
  fun `REF properties resolve via lookup and are checked for existence when mapped directly`() {
    val appName = "Import Ref App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val companyEntityId = addEntity(appId, versionId, "Company")
    val codePropertyId = addProperty(appId, versionId, companyEntityId, "Code", "STRING", nullable = false)
    val contactEntityId = addEntity(appId, versionId, "Contact")
    val namePropertyId = addProperty(appId, versionId, contactEntityId, "Name", "STRING", nullable = false)
    val companyPropertyId = addReferenceProperty(appId, versionId, contactEntityId, "Company", companyEntityId)
    val installedAppId = publishAndInstall(appId, appName)

    val companyAppDataId = AppDataId(UUID.randomUUID().toString())
    appDataRepository.save(
      AppData(
        id = companyAppDataId,
        userId = "test-import-trigger-user",
        installedAppId = InstalledAppId(installedAppId),
        appVersion = VersionNumber("1.0.0"),
        lastValidatedWithVersion = VersionNumber("1.0.0"),
        entityType = EntityDefinitionId(companyEntityId),
        objectVersion = 1,
        createdAt = Instant.now(),
        lastChangedAt = Instant.now(),
        data = mapOf(codePropertyId to "ACME"),
      ),
    )

    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString()))
      .thenReturn("""{"items":[{"name":"Alice","companyCode":"ACME"}]}""".right())
    triggerImport(installedAppId, createConnection(), contactEntityId)
    val lookupImportId = triggerImportAndGetId(installedAppId)

    given()
      .contentType("application/json")
      .body(
        """
        {
          "fieldMappings": [
            { "targetPropertyId": "$namePropertyId", "sourcePath": "name", "conversion": "NONE", "fallbackValue": null },
            {
              "targetPropertyId": "$companyPropertyId", "sourcePath": null, "conversion": "NONE", "fallbackValue": null,
              "referenceLookup": { "criteria": [ { "targetPropertyId": "$codePropertyId", "sourcePath": "companyCode" } ] }
            }
          ]
        }
        """.trimIndent(),
      )
      .`when`()
      .post("/ui/user/imports/$lookupImportId/mapping")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    given()
      .`when`()
      .post("/ui/user/imports/$lookupImportId/dry-run/accept")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
    awaitImportAccepted(lookupImportId)

    val savedContact = appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId(installedAppId), EntityDefinitionId(contactEntityId)).single()
    assertTrue(savedContact.data[companyPropertyId] == companyAppDataId.value, "Expected the Contact's Company reference to resolve to the seeded Company's id")

    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString()))
      .thenReturn("""{"items":[{"name":"Bob"}]}""".right())
    triggerImport(installedAppId, createConnection(), contactEntityId)
    val directImportId = triggerImportAndGetId(installedAppId)

    given()
      .contentType("application/json")
      .body(
        """
        {
          "fieldMappings": [
            { "targetPropertyId": "$namePropertyId", "sourcePath": "name", "conversion": "NONE", "fallbackValue": null },
            { "targetPropertyId": "$companyPropertyId", "sourcePath": null, "conversion": "NONE", "fallbackValue": "not-a-real-company-id" }
          ]
        }
        """.trimIndent(),
      )
      .`when`()
      .post("/ui/user/imports/$directImportId/mapping")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val dryRunHtml = given()
      .`when`()
      .get("/ui/user/imports/$directImportId/dry-run")
      .then()
      .statusCode(200)
      .extract().body().asString()
    assertTrue(dryRunHtml.contains("data-testid=\"invalid-count\">1<"), "Expected the fallback value to be reported invalid: it does not point to an existing Company instance")

    given()
      .`when`()
      .post("/ui/user/imports/$directImportId/dry-run/accept")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
    awaitImportAccepted(directImportId)

    val savedContacts = appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId(installedAppId), EntityDefinitionId(contactEntityId))
    assertTrue(
      savedContacts.single().data == savedContact.data,
      "Expected only the earlier lookup-resolved Contact to remain; the invalid direct-ref Contact must be discarded",
    )
  }

  @Test
  fun `fan-in mapping into a unique mandatory property is ready without a fallback and discards records without or with an already used value`() {
    val appName = "Import Fan-In App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val entityId = addEntity(appId, versionId, "Company")
    val propertyId = addProperty(appId, versionId, entityId, "Code", "STRING", nullable = false)
    addUniqueKeyConstraint(appId, versionId, entityId, propertyId)
    val installedAppId = publishAndInstall(appId, appName)

    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString()))
      .thenReturn("""{"items":[{"code":"ACME"},{"other":"no code here"},{"code":"ACME"},{"code":"GLOBEX"}]}""".right())
    triggerImport(installedAppId, createConnection(), entityId)
    val importId = triggerImportAndGetId(installedAppId)

    given()
      .contentType("application/json")
      .body(
        """
        {
          "fieldMappings": [
            { "targetPropertyId": "$propertyId", "sourcePath": "code", "conversion": "NONE", "fallbackValue": null }
          ]
        }
        """.trimIndent(),
      )
      .`when`()
      .post("/ui/user/imports/$importId/mapping")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val mappingHtml = given()
      .`when`()
      .get("/ui/user/imports/$importId/mapping")
      .then()
      .statusCode(200)
      .extract().body().asString()
    assertTrue(
      mappingHtml.contains("<a href=\"/ui/user/imports/$importId/dry-run\""),
      "Expected the mapping to be READY without a fallback: a static fallback would collide with the unique constraint",
    )

    val dryRunHtml = given()
      .`when`()
      .get("/ui/user/imports/$importId/dry-run")
      .then()
      .statusCode(200)
      .extract().body().asString()
    assertTrue(dryRunHtml.contains("data-testid=\"valid-count\">2<"), "Expected the two first-occurrence records (ACME, GLOBEX) to be valid")
    assertTrue(dryRunHtml.contains("data-testid=\"skipped-count\">2<"), "Expected the missing-value and duplicate-value records to be reported as expected fan-in skips")
    assertTrue(dryRunHtml.contains("data-testid=\"invalid-count\">0<"), "Expected no record to be reported as invalid: fan-in skips are not defects")
    assertTrue(dryRunHtml.contains("data-testid=\"skipped-reason-row\""), "Expected the skipped records to be aggregated into a reasons table instead of listing every object")
    assertTrue(dryRunHtml.contains("data-testid=\"skipped-reason-badge\""), "Expected the skipped reasons to carry the expected/fan-in badge, not the invalid styling")

    given()
      .`when`()
      .post("/ui/user/imports/$importId/dry-run/accept")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
    awaitImportAccepted(importId)

    val savedCompanies = appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId(installedAppId), EntityDefinitionId(entityId))
    assertTrue(
      savedCompanies.map { it.data[propertyId] }.toSet() == setOf("ACME", "GLOBEX"),
      "Expected only the first record per unique value to be saved, records without a value or with an already used value discarded",
    )
  }

  @Test
  fun `opening an import always shows the furthest step it has reached, not the overview`() {
    val app = installAppWithMandatoryStringProperty()
    triggerImportWithSingleDataPath(app.installedAppId, app.entityId)
    val importId = triggerImportAndGetId(app.installedAppId)

    val htmlAtDataIdentified = given()
      .`when`()
      .get("/ui/user/imports/$importId")
      .then()
      .statusCode(200)
      .extract().body().asString()
    assertTrue(htmlAtDataIdentified.contains("data-testid=\"mapping-title\""), "Expected opening the import to land on the Mapping step, its furthest reached step")

    given()
      .contentType("application/json")
      .body(
        """{"fieldMappings": [{ "targetPropertyId": "${app.propertyId}", "sourcePath": "name", "conversion": "NONE", "fallbackValue": null }]}""",
      )
      .`when`()
      .post("/ui/user/imports/$importId/mapping")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val htmlAtReady = given()
      .`when`()
      .get("/ui/user/imports/$importId")
      .then()
      .statusCode(200)
      .extract().body().asString()
    assertTrue(htmlAtReady.contains("data-testid=\"dry-run-title\""), "Expected opening the import to land on the Dry-Run step once it's READY")

    val overviewHtml = given()
      .`when`()
      .get("/ui/user/imports/$importId/overview")
      .then()
      .statusCode(200)
      .extract().body().asString()
    assertTrue(overviewHtml.contains("data-testid=\"import-job-title\""), "Expected the overview/metadata page to remain reachable through its own explicit URL")
  }

  @Test
  fun `import job overview page links the target entity to its dedicated page within the app installation`() {
    val appName = "Import Entity Link App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    addEntity(appId, versionId, "Other")
    val targetEntityId = addEntity(appId, versionId, "Target")
    val installedAppId = publishAndInstall(appId, appName)

    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"foo":"bar"}""".right())
    triggerImport(installedAppId, createConnection(), targetEntityId)
    val importId = triggerImportAndGetId(installedAppId)

    val html = given()
      .`when`()
      .get("/ui/user/imports/$importId/overview")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(
      html.contains("href=\"/ui/user/apps/$installedAppId/entities/$targetEntityId\""),
      "Expected the target entity to link to its dedicated page within the app installation (the app has more than one entity type)",
    )
  }

  @Test
  fun `import job overview page shows the auto-detected JSON structure for the selected data path`() {
    val (installedAppId, entityId) = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"items":[{"name":"Alice","age":30}]}""".right())
    triggerImport(installedAppId, createConnection(), entityId)
    val importId = triggerImportAndGetId(installedAppId)

    val html = given()
      .`when`()
      .get("/ui/user/imports/$importId/overview")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-bs-target=\"#dataPathStructure\""), "Expected clicking the data path to expand the inline JSON structure panel, not open a modal")
    assertTrue(html.contains("data-testid=\"data-path-structure-table\""), "Expected the JSON structure table to be rendered")
    assertTrue(html.contains("data-testid=\"data-path-structure-row\""), "Expected a structure row per detected field")
    assertTrue(html.contains("data-testid=\"data-path-structure-selected-badge\""), "Expected the row matching the selected data path to be marked")
    assertTrue(html.contains("data-testid=\"data-path-schema-mandatory-badge\""), "Expected mandatory fields inside the selected data path to be flagged")
  }

  @Test
  fun `import job overview page has an explicit Quelle breadcrumb item, the wizard step nav, and no delete button`() {
    val (installedAppId, entityId) = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"foo":"bar"}""".right())
    triggerImport(installedAppId, createConnection(), entityId)
    val importId = triggerImportAndGetId(installedAppId)

    val html = given()
      .`when`()
      .get("/ui/user/imports/$importId/overview")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"breadcrumb-source\">Quelle<"), "Expected an explicit, non-implicit Quelle breadcrumb item")
    assertTrue(html.contains("href=\"/ui/user/imports/$importId\" class=\"breadcrumb-link\" data-testid=\"breadcrumb-import-name\""), "Expected the job breadcrumb item to link to the job root, separately from the Quelle item")
    assertTrue(html.contains("app-wizard"), "Expected the step navigator to render as the full-width wizard progress control")
    assertTrue(!html.contains("data-testid=\"delete-import-button\""), "Expected the delete action to no longer be offered on the job page, only in the imports table")
  }

  @Test
  fun `mapping and dry-run pages no longer nest under a Quelle breadcrumb item, since Quelle is a sibling step, not their parent`() {
    val app = installAppWithMandatoryStringProperty()
    triggerImportWithSingleDataPath(app.installedAppId, app.entityId)
    val importId = triggerImportAndGetId(app.installedAppId)

    val mappingHtml = given()
      .`when`()
      .get("/ui/user/imports/$importId/mapping")
      .then()
      .statusCode(200)
      .extract().body().asString()
    assertTrue(!mappingHtml.contains("data-testid=\"breadcrumb-source\""), "Expected the mapping page breadcrumb to go directly from the job to Mapping, without an intermediate Quelle item")
    assertTrue(mappingHtml.contains("data-testid=\"breadcrumb-import-name\">Contact<"), "Expected the job breadcrumb item to be present")

    given()
      .contentType("application/json")
      .body(
        """{"fieldMappings": [{ "targetPropertyId": "${app.propertyId}", "sourcePath": "name", "conversion": "NONE", "fallbackValue": null }]}""",
      )
      .`when`()
      .post("/ui/user/imports/$importId/mapping")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val dryRunHtml = given()
      .`when`()
      .get("/ui/user/imports/$importId/dry-run")
      .then()
      .statusCode(200)
      .extract().body().asString()
    assertTrue(!dryRunHtml.contains("data-testid=\"breadcrumb-source\""), "Expected the dry-run page breadcrumb to go directly from the job to Dry-Run, without an intermediate Quelle item")
  }

  @Test
  fun `dry run page offers Ersetzen and Hinzufügen right-aligned with icons, and a single-open accordion for the count details`() {
    val (app, importId) = readyImportWithOneValidAndOneInvalidRecord()

    val html = given()
      .`when`()
      .get("/ui/user/imports/$importId/dry-run")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("justify-content-end"), "Expected the Ersetzen/Hinzufügen buttons to be right-aligned")
    assertTrue(html.contains("bi-upload"), "Expected the Ersetzen/Hinzufügen buttons to carry an icon in addition to their text")
    assertTrue(
      html.contains("id=\"valid-details\" data-bs-parent=\"#dryRunDetailsAccordion\"") &&
        html.contains("id=\"invalid-details\" data-bs-parent=\"#dryRunDetailsAccordion\"") &&
        html.contains("id=\"skipped-details\" data-bs-parent=\"#dryRunDetailsAccordion\""),
      "Expected the valid/invalid/skipped detail panels to share one Bootstrap collapse parent so opening one closes the others",
    )
    assertTrue(
      !html.contains("data-bs-toggle=\"collapse\" data-bs-target=\"#valid-details\"") &&
        !html.contains("data-bs-toggle=\"collapse\" data-bs-target=\"#invalid-details\"") &&
        !html.contains("data-bs-toggle=\"collapse\" data-bs-target=\"#skipped-details\""),
      "Expected the count cards to no longer use Bootstrap's declarative collapse toggle, since that always toggles " +
        "(closing an already-open section); a JS click handler now calls show() so exactly one section always stays open",
    )
  }

  @Test
  fun `Quelle, Mapping and Dry-Run pages share a constant connection-app-entity heading with no separate status badge`() {
    val appName = "Import Heading App ${System.nanoTime()}"
    val connectionName = "Import Heading Connection ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val entityId = addEntity(appId, versionId, "Contact")
    val propertyId = addProperty(appId, versionId, entityId, "Name", "STRING", nullable = false)
    val installedAppId = publishAndInstall(appId, appName)

    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"items":[{"name":"Alice"}]}""".right())
    triggerImport(installedAppId, createConnection(name = connectionName), entityId)
    val importId = triggerImportAndGetId(installedAppId)

    val expectedHeading = "$connectionName: $appName Contact"

    val overviewHtml = given().`when`().get("/ui/user/imports/$importId/overview").then().statusCode(200).extract().body().asString()
    assertTrue(overviewHtml.contains("data-testid=\"import-job-title\">$expectedHeading<"), "Expected the constant connection/app/entity heading on the Quelle page")
    assertTrue(!overviewHtml.contains("data-testid=\"import-job-status\""), "Expected the status badge to no longer be rendered next to the heading")

    given()
      .contentType("application/json")
      .body("""{"fieldMappings": [{ "targetPropertyId": "$propertyId", "sourcePath": "name", "conversion": "NONE", "fallbackValue": null }]}""")
      .`when`()
      .post("/ui/user/imports/$importId/mapping")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val mappingHtml = given().`when`().get("/ui/user/imports/$importId/mapping").then().statusCode(200).extract().body().asString()
    assertTrue(mappingHtml.contains("data-testid=\"mapping-title\">$expectedHeading<"), "Expected the same constant heading on the Mapping page")
    assertTrue(!mappingHtml.contains("data-testid=\"mapping-status\""), "Expected the status badge to no longer be rendered next to the Mapping heading")

    val dryRunHtml = given().`when`().get("/ui/user/imports/$importId/dry-run").then().statusCode(200).extract().body().asString()
    assertTrue(dryRunHtml.contains("data-testid=\"dry-run-title\">$expectedHeading<"), "Expected the same constant heading on the Dry-Run page")

    assertTrue(dryRunHtml.contains("app-section-label small\">Imports<"), "Expected the valid counter to be relabeled to Imports")
    assertTrue(dryRunHtml.contains("app-section-label small\">Fehler<"), "Expected the invalid counter to be relabeled to Fehler")
    assertTrue(dryRunHtml.contains("app-section-label small\">Ignoriert<"), "Expected the skipped counter to be relabeled to Ignoriert")

    assertTrue(dryRunHtml.contains("data-testid=\"valid-objects-heading\">Details<"), "Expected the valid detail heading to use the constant Details heading")
    assertTrue(dryRunHtml.contains("data-testid=\"invalid-objects-heading\">Details<"), "Expected the invalid detail heading to use the constant Details heading")
    assertTrue(dryRunHtml.contains("data-testid=\"skipped-reasons-heading\">Details<"), "Expected the skipped detail heading to use the constant Details heading")

    assertTrue(
      dryRunHtml.contains("class=\"collapse show mb-4\" id=\"valid-details\""),
      "Expected the valid objects' details to be opened automatically since there are no errors",
    )
    assertTrue(
      dryRunHtml.contains("dry-run-count-toggle active\" role=\"button\" tabindex=\"0\" aria-expanded=\"true\" aria-controls=\"valid-details\""),
      "Expected a subtle active highlight on the valid count card matching the auto-opened details section",
    )
  }

  @Test
  fun `all import job pages show the deactivated app banner once the app has been deactivated`() {
    val appName = "Deactivated Import Banner App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val entityId = addEntity(appId, versionId, "Contact")
    val propertyId = addProperty(appId, versionId, entityId, "Name", "STRING", nullable = false)
    val installedAppId = publishAndInstall(appId, appName)
    triggerImportWithSingleDataPath(installedAppId, entityId)
    val importId = triggerImportAndGetId(installedAppId)

    given()
      .contentType("application/json")
      .body("""{"fieldMappings": [{ "targetPropertyId": "$propertyId", "sourcePath": "name", "conversion": "NONE", "fallbackValue": null }]}""")
      .`when`()
      .post("/ui/user/imports/$importId/mapping")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    given()
      .`when`()
      .post("/ui/developer/apps/$appId/deactivate")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val overviewHtml = given().`when`().get("/ui/user/imports/$importId/overview").then().statusCode(200).extract().body().asString()
    assertTrue(overviewHtml.contains("data-testid=\"app-deactivated-banner\""), "Expected the deactivated banner on the Quelle/overview page")

    val filterHtml = given().`when`().get("/ui/user/imports/$importId/filter").then().statusCode(200).extract().body().asString()
    assertTrue(filterHtml.contains("data-testid=\"app-deactivated-banner\""), "Expected the deactivated banner on the filter page")

    val mappingHtml = given().`when`().get("/ui/user/imports/$importId/mapping").then().statusCode(200).extract().body().asString()
    assertTrue(mappingHtml.contains("data-testid=\"app-deactivated-banner\""), "Expected the deactivated banner on the mapping page")

    val dryRunHtml = given().`when`().get("/ui/user/imports/$importId/dry-run").then().statusCode(200).extract().body().asString()
    assertTrue(dryRunHtml.contains("data-testid=\"app-deactivated-banner\""), "Expected the deactivated banner on the dry-run page")
  }

  @Test
  fun `replacing a dry run clears existing data for the target entity first, so a record that only collided with it gets saved`() {
    val appName = "Import Replace App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val entityId = addEntity(appId, versionId, "Company")
    val propertyId = addProperty(appId, versionId, entityId, "Code", "STRING", nullable = false)
    addUniqueKeyConstraint(appId, versionId, entityId, propertyId)
    val installedAppId = publishAndInstall(appId, appName)

    val existingId = AppDataId(UUID.randomUUID().toString())
    appDataRepository.save(
      AppData(
        id = existingId,
        userId = "test-import-trigger-user",
        installedAppId = InstalledAppId(installedAppId),
        appVersion = VersionNumber("1.0.0"),
        lastValidatedWithVersion = VersionNumber("1.0.0"),
        entityType = EntityDefinitionId(entityId),
        objectVersion = 1,
        createdAt = Instant.now(),
        lastChangedAt = Instant.now(),
        data = mapOf(propertyId to "ACME"),
      ),
    )

    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString()))
      .thenReturn("""{"items":[{"code":"ACME"}]}""".right())
    triggerImport(installedAppId, createConnection(), entityId)
    val importId = triggerImportAndGetId(installedAppId)

    given()
      .contentType("application/json")
      .body("""{"fieldMappings": [{ "targetPropertyId": "$propertyId", "sourcePath": "code", "conversion": "NONE", "fallbackValue": null }]}""")
      .`when`()
      .post("/ui/user/imports/$importId/mapping")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val dryRunHtml = given()
      .`when`()
      .get("/ui/user/imports/$importId/dry-run")
      .then()
      .statusCode(200)
      .extract().body().asString()
    assertTrue(dryRunHtml.contains("data-testid=\"skipped-count\">1<"), "Expected the record to be skipped: it collides with the pre-existing Company (add semantics)")

    given()
      .`when`()
      .post("/ui/user/imports/$importId/dry-run/accept?mode=REPLACE")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
    awaitImportAccepted(importId)

    val savedCompanies = appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId(installedAppId), EntityDefinitionId(entityId))
    assertTrue(savedCompanies.size == 1, "Expected exactly one Company to remain after replace")
    assertTrue(savedCompanies.single().id != existingId, "Expected the pre-existing Company to have been deleted and replaced by the newly imported one")
    assertTrue(savedCompanies.single().data[propertyId] == "ACME", "Expected the record that only collided with the now-deleted existing data to be saved")
  }

  @Test
  fun `schema panel offcanvas is rendered identically on the Quelle, Filter and Mapping pages, with value range and string length as secondary info`() {
    val (installedAppId, entityId) = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString()))
      .thenReturn("""{"items":[{"name":"Alice","age":30},{"name":"Bob","age":42}]}""".right())
    triggerImport(installedAppId, createConnection(), entityId)
    val importId = triggerImportAndGetId(installedAppId)

    val overviewHtml = given().`when`().get("/ui/user/imports/$importId/overview").then().statusCode(200).extract().body().asString()
    assertTrue(overviewHtml.contains("data-testid=\"schema-panel-trigger\""), "Expected the schema panel trigger button on the Quelle page")
    assertTrue(overviewHtml.contains("data-testid=\"schema-panel-table\""), "Expected the schema panel table on the Quelle page")
    assertTrue(overviewHtml.contains("data-testid=\"schema-panel-value-range\""), "Expected the numeric value range to be shown for the age field")
    assertTrue(overviewHtml.contains("data-testid=\"schema-panel-string-length\""), "Expected the string length hint to be shown for the name field")

    val filterHtml = given().`when`().get("/ui/user/imports/$importId/filter").then().statusCode(200).extract().body().asString()
    assertTrue(filterHtml.contains("data-testid=\"schema-panel-trigger\""), "Expected the schema panel trigger button on the Filter page")
    assertTrue(filterHtml.contains("data-testid=\"schema-panel-value-range\""), "Expected the numeric value range to be shown on the Filter page")

    val mappingHtml = given().`when`().get("/ui/user/imports/$importId/mapping").then().statusCode(200).extract().body().asString()
    assertTrue(mappingHtml.contains("data-testid=\"schema-panel-trigger\""), "Expected the schema panel trigger button on the Mapping page")
    assertTrue(mappingHtml.contains("data-testid=\"schema-panel-string-length\""), "Expected the string length hint to be shown on the Mapping page")
  }

  @Test
  fun `import steps hide their labels below the sm breakpoint, showing only the icon and step number`() {
    val (installedAppId, entityId) = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"foo":"bar"}""".right())
    triggerImport(installedAppId, createConnection(), entityId)
    val importId = triggerImportAndGetId(installedAppId)

    val html = given().`when`().get("/ui/user/imports/$importId/overview").then().statusCode(200).extract().body().asString()
    assertTrue(html.contains("<span class=\"d-none d-sm-inline\">Quelle</span>"), "Expected the step labels to be hidden below the sm breakpoint")
  }
}
