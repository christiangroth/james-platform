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
      .formParam("url", url)
    (if (bearerToken != null) request.formParam("bearerToken", bearerToken) else request)
      .`when`()
      .post("/ui/user/import-connections")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val tableHtml = given()
      .`when`()
      .get("/ui/user/import-connections/table")
      .then()
      .statusCode(200)
      .extract().body().asString()

    return Regex("""data-connection-id="([^"]+)"\s+data-connection-name="${Regex.escape(name)}"""")
      .find(tableHtml)?.groupValues?.get(1)
      ?: error("Expected connection '$name' in the rendered connections table")
  }

  private fun triggerImport(installedAppId: String, connectionId: String, entityId: String) {
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("connectionId", connectionId)
      .formParam("targetEntityDefinitionId", entityId)
      .`when`()
      .post("/ui/user/imports/$installedAppId")
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
      .get("/ui/user/imports/$installedAppId/table")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(tableHtml.contains("data-testid=\"imports-table\""), "Expected the imports table to be rendered")
    assertTrue(tableHtml.contains("data-testid=\"import-status\""), "Expected a status cell for the created import job")
  }

  @Test
  fun `trigger import reports an error when the response is not a JSON object`() {
    val (installedAppId, entityId) = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("[1,2,3]".right())

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("connectionId", createConnection())
      .formParam("targetEntityDefinitionId", entityId)
      .`when`()
      .post("/ui/user/imports/$installedAppId")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `trigger import reports an error when no connection is selected`() {
    val (installedAppId, entityId) = installApp()

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("connectionId", "")
      .formParam("targetEntityDefinitionId", entityId)
      .`when`()
      .post("/ui/user/imports/$installedAppId")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `trigger import reports an error when the connection does not exist`() {
    val (installedAppId, entityId) = installApp()

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("connectionId", "unknown-connection")
      .formParam("targetEntityDefinitionId", entityId)
      .`when`()
      .post("/ui/user/imports/$installedAppId")
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
      .formParam("connectionId", createConnection())
      .formParam("targetEntityDefinitionId", entityId)
      .`when`()
      .post("/ui/user/imports/$installedAppId")
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
      .formParam("connectionId", createConnection())
      .formParam("targetEntityDefinitionId", entityId)
      .`when`()
      .post("/ui/user/imports/$installedAppId")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
      .body("errorDetails[0]", equalTo("Server responded with HTTP status 503."))
  }

  @Test
  fun `delete import job removes it from the table`() {
    val (installedAppId, entityId) = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"foo":"bar"}""".right())

    triggerImport(installedAppId, createConnection(), entityId)

    val tableHtml = given()
      .`when`()
      .get("/ui/user/imports/$installedAppId/table")
      .then()
      .statusCode(200)
      .extract().body().asString()
    val importId = Regex("""data-import-id="([^"]+)"""").find(tableHtml)?.groupValues?.get(1)
      ?: error("Expected an import id in the rendered table")

    given()
      .`when`()
      .post("/ui/user/imports/$importId/delete")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val afterDeleteHtml = given()
      .`when`()
      .get("/ui/user/imports/$installedAppId/table")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(afterDeleteHtml.contains("data-testid=\"no-imports-message\""), "Expected the empty-state message after deleting the only import job")
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
      .get("/ui/user/imports/$installedAppId/table")
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

    val tableHtml = given()
      .`when`()
      .get("/ui/user/imports/$installedAppId/table")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(tableHtml.contains("data-testid=\"selected-data-path\">items<"), "Expected the auto-selected data path to be rendered")
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
  fun `select data path succeeds and updates the table`() {
    val (installedAppId, entityId) = installApp()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"a":[{"x":1}],"b":[{"y":1},{"y":2}]}""".right())

    triggerImport(installedAppId, createConnection(), entityId)
    val importId = triggerImportAndGetId(installedAppId)

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("dataPath", "b")
      .`when`()
      .post("/ui/user/imports/$importId/select-path")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val tableHtml = given()
      .`when`()
      .get("/ui/user/imports/$installedAppId/table")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(tableHtml.contains("data-testid=\"selected-data-path\">b<"), "Expected the manually selected data path to be rendered")
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

    assertTrue(html.contains("data-testid=\"mapping-status\">Bereit<"), "Expected the import job status to be READY after a valid, complete mapping")
  }

  @Test
  fun `saving an incomplete mapping keeps the import job at DATA_IDENTIFIED and reports the missing mandatory field`() {
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

    val html = given()
      .`when`()
      .get("/ui/user/imports/$importId/mapping")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(html.contains("data-testid=\"mapping-status\">Datenpfad identifiziert<"), "Expected the import job to remain DATA_IDENTIFIED with an incomplete mapping")
    assertTrue(html.contains("data-testid=\"mapping-issue\""), "Expected a validation issue to be rendered for the unmapped mandatory field")
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
    assertTrue(html.contains("data-testid=\"dry-run-issue\""), "Expected the missing mandatory value issue to be rendered")
    assertTrue(html.contains("data-testid=\"statically-checked-badge\""), "Expected the missing mandatory value issue to be flagged as already checked statically")
  }

  @Test
  fun `accepting a dry run saves the valid object, discards the invalid one and deletes the import job`() {
    val (app, importId) = readyImportWithOneValidAndOneInvalidRecord()

    given()
      .`when`()
      .post("/ui/user/imports/$importId/dry-run/accept")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
      .body("redirectUrl", equalTo("/ui/user/imports/${app.installedAppId}"))

    val tableHtml = given()
      .`when`()
      .get("/ui/user/imports/${app.installedAppId}/table")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(tableHtml.contains("data-testid=\"no-imports-message\""), "Expected the import job to have been deleted after accepting the dry run")
  }

  @Test
  fun `reference lookup resolves a mapped REF property to the id of the matching referenced entity`() {
    val appName = "Import Lookup App ${System.nanoTime()}"
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
    val importId = triggerImportAndGetId(installedAppId)

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
    assertTrue(mappingHtml.contains("data-testid=\"mapping-status\">Bereit<"), "Expected the import job to be READY once the reference lookup is fully configured")

    given()
      .`when`()
      .post("/ui/user/imports/$importId/dry-run/accept")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val savedContact = appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId(installedAppId), EntityDefinitionId(contactEntityId)).single()
    assertTrue(savedContact.data[companyPropertyId] == companyAppDataId.value, "Expected the Contact's Company reference to resolve to the seeded Company's id")
  }

  @Test
  fun `directly mapped REF property pointing to a non-existing instance is reported invalid and discarded on accept`() {
    val appName = "Import Direct Ref App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val companyEntityId = addEntity(appId, versionId, "Company")
    val contactEntityId = addEntity(appId, versionId, "Contact")
    val namePropertyId = addProperty(appId, versionId, contactEntityId, "Name", "STRING", nullable = false)
    val companyPropertyId = addReferenceProperty(appId, versionId, contactEntityId, "Company", companyEntityId)
    val installedAppId = publishAndInstall(appId, appName)

    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString()))
      .thenReturn("""{"items":[{"name":"Alice"}]}""".right())
    triggerImport(installedAppId, createConnection(), contactEntityId)
    val importId = triggerImportAndGetId(installedAppId)

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
    assertTrue(mappingHtml.contains("data-testid=\"mapping-status\">Bereit<"), "Expected the import job to be READY: the static fallback check does not know the referenced entity's persisted data")

    val dryRunHtml = given()
      .`when`()
      .get("/ui/user/imports/$importId/dry-run")
      .then()
      .statusCode(200)
      .extract().body().asString()
    assertTrue(dryRunHtml.contains("data-testid=\"invalid-count\">1<"), "Expected the fallback value to be reported invalid: it does not point to an existing Company instance")

    given()
      .`when`()
      .post("/ui/user/imports/$importId/dry-run/accept")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val savedContacts = appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId(installedAppId), EntityDefinitionId(contactEntityId))
    assertTrue(savedContacts.isEmpty(), "Expected the invalid Contact record to be discarded rather than saved with a dangling Company reference")
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
      mappingHtml.contains("data-testid=\"mapping-status\">Bereit<"),
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
    assertTrue(dryRunHtml.contains("data-testid=\"dry-run-skipped-object\""), "Expected the skipped records to be rendered in their own accordion")
    assertTrue(dryRunHtml.contains("data-testid=\"expected-skip-badge\""), "Expected the skipped records' issues to carry the expected/fan-in badge, not the invalid styling")

    given()
      .`when`()
      .post("/ui/user/imports/$importId/dry-run/accept")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val savedCompanies = appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId(installedAppId), EntityDefinitionId(entityId))
    assertTrue(
      savedCompanies.map { it.data[propertyId] }.toSet() == setOf("ACME", "GLOBEX"),
      "Expected only the first record per unique value to be saved, records without a value or with an already used value discarded",
    )
  }
}
