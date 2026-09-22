package de.chrgroth.james.platform.adapter.`in`.web

import arrow.core.right
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
@TestSecurity(user = "test-import-definition-user", roles = ["DEVELOPER", "DATA_IMPORT"])
class UserImportDefinitionResourceTests {

  @InjectMock
  lateinit var importFetch: ImportFetchPort

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @BeforeEach
  fun setup() {
    if (userRepository.findByUsername(Username("test-import-definition-user")) == null) {
      userRepository.save(
        User(
          id = UserId(UUID.randomUUID().toString()),
          username = Username("test-import-definition-user"),
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

  private fun installAppWithMandatoryStringProperty(): InstalledAppWithEntity {
    val appName = "Import Definition App ${System.nanoTime()}"
    val (appId, versionId) = createApp(appName)
    val entityId = addEntity(appId, versionId, "Contact")
    val propertyId = addProperty(appId, versionId, entityId, "Name", "STRING", nullable = false)
    val installedAppId = publishAndInstall(appId, appName)
    return InstalledAppWithEntity(installedAppId, entityId, propertyId)
  }

  private fun createConnection(name: String = "Definition Test Connection ${System.nanoTime()}"): String {
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", name)
      .formParam("baseUrl", "https://example.com/data")
      .`when`()
      .post("/ui/user/imports/connections")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
    return name
  }

  private fun triggerImportAndGetId(installedAppId: String, connectionName: String, entityId: String): String {
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("installedAppId", installedAppId)
      .formParam("connectionId", connectionIdFor(connectionName))
      .formParam("targetEntityDefinitionId", entityId)
      .`when`()
      .post("/ui/user/imports")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val tableHtml = given().`when`().get("/ui/user/imports/table").then().statusCode(200).extract().body().asString()
    return Regex("""data-import-id="([^"]+)"""").find(tableHtml)?.groupValues?.get(1)
      ?: error("Expected an import id in the rendered table")
  }

  private fun connectionIdFor(name: String): String {
    val tableHtml = given().`when`().get("/ui/user/imports/connections/table").then().statusCode(200).extract().body().asString()
    return Regex("""data-connection-id="([^"]+)"\s+data-connection-name="${Regex.escape(name)}"""")
      .find(tableHtml)?.groupValues?.get(1)
      ?: error("Expected connection '$name' in the rendered connections table")
  }

  private fun saveMapping(importId: String, propertyId: String) {
    given()
      .contentType("application/json")
      .body("""{"fieldMappings": [{ "targetPropertyId": "$propertyId", "sourcePath": "name", "conversion": "NONE", "fallbackValue": null }]}""")
      .`when`()
      .post("/ui/user/imports/$importId/mapping")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
  }

  /**
   * Installs an app, creates a connection, triggers an import (auto-selecting its single data path) and maps it to
   * READY, returning the underlying definition's row-matching connection name.
   */
  private fun createConfiguredDefinition(): Triple<String, String, InstalledAppWithEntity> {
    val app = installAppWithMandatoryStringProperty()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"items":[{"name":"Alice"},{"name":"Bob"}]}""".right())
    val connectionName = createConnection()
    val importId = triggerImportAndGetId(app.installedAppId, connectionName, app.entityId)
    saveMapping(importId, app.propertyId)
    val definitionId = definitionIdForConnection(connectionName)
    return Triple(definitionId, connectionName, app)
  }

  /**
   * Splits the rendered `imports_table` fragment into its per-definition blocks (issue #679: an `<h2>` heading plus
   * a dedicated jobs table per definition, instead of one shared `<table>` row per definition) by slicing between
   * successive `data-testid="definition-block"` markers - a nesting-aware `[\s\S]*?</div>` regex would stop at the
   * first nested `</div>` inside a block instead of its own closing tag.
   */
  private fun definitionBlocks(tableHtml: String): List<String> {
    val marker = "data-testid=\"definition-block\""
    val starts = Regex(Regex.escape(marker)).findAll(tableHtml).map { it.range.first }.toList()
    return starts.mapIndexed { index, start -> tableHtml.substring(start, starts.getOrNull(index + 1) ?: tableHtml.length) }
  }

  private fun definitionRow(connectionName: String): String {
    val tableHtml = given().`when`().get("/ui/user/imports/table").then().statusCode(200).extract().body().asString()
    return definitionBlocks(tableHtml).firstOrNull { it.contains(connectionName) }
      ?: error("Expected a definition block for connection '$connectionName'")
  }

  private fun definitionIdForConnection(connectionName: String): String =
    Regex("""data-definition-id="([^"]+)"""").find(definitionRow(connectionName))?.groupValues?.get(1)
      ?: error("Expected a definition id in the row for connection '$connectionName'")

  @Test
  fun `definitions table lists the source, app and entity for a configured definition`() {
    val (_, connectionName, app) = createConfiguredDefinition()

    val row = definitionRow(connectionName)

    assertTrue(row.contains(connectionName), "Expected the connection name in the source column")
    assertTrue(row.contains("Contact"), "Expected the target entity name")
    assertTrue(row.contains("Manuell"), "Expected an unscheduled definition to show the manual label")
  }

  @Test
  fun `run endpoint triggers an unattended run for a fully configured definition`() {
    val (definitionId, _, _) = createConfiguredDefinition()

    given()
      .`when`()
      .post("/ui/user/imports/definitions/$definitionId/run")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
  }

  @Test
  fun `run endpoint reports an error for an unknown definition id`() {
    given()
      .`when`()
      .post("/ui/user/imports/definitions/unknown-definition/run")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `run endpoint reports an error for a definition without a mapping configured yet`() {
    val app = installAppWithMandatoryStringProperty()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"items":[{"name":"Alice"}]}""".right())
    val connectionName = createConnection()
    triggerImportAndGetId(app.installedAppId, connectionName, app.entityId)
    val definitionId = definitionIdForConnection(connectionName)

    given()
      .`when`()
      .post("/ui/user/imports/definitions/$definitionId/run")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `start endpoint starts a fresh interactive job for a fully configured definition and redirects into its wizard`() {
    val (definitionId, _, _) = createConfiguredDefinition()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"items":[{"name":"Alice"},{"name":"Bob"}]}""".right())

    val response = given()
      .`when`()
      .post("/ui/user/imports/definitions/$definitionId/start")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
      .extract().body().jsonPath()

    val redirectUrl = response.getString("redirectUrl")
    assertTrue(redirectUrl != null && redirectUrl.startsWith("/ui/user/imports/"), "Expected a redirect into the newly started job's wizard")
  }

  @Test
  fun `start endpoint works for a definition that was never fully configured, unlike run`() {
    val app = installAppWithMandatoryStringProperty()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"items":[{"name":"Alice"}]}""".right())
    val connectionName = createConnection()
    triggerImportAndGetId(app.installedAppId, connectionName, app.entityId)
    val definitionId = definitionIdForConnection(connectionName)

    given()
      .`when`()
      .post("/ui/user/imports/definitions/$definitionId/start")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))
  }

  @Test
  fun `start endpoint reports an error for an unknown or unowned definition id, mirroring the run and delete endpoints' ownership check`() {
    given()
      .`when`()
      .post("/ui/user/imports/definitions/unknown-definition/start")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `merged imports page wires a start-job action into a definition block with no in-progress job`() {
    val (_, connectionName, _) = createConfiguredDefinition()

    val row = definitionRow(connectionName)

    assertTrue(row.contains("data-testid=\"start-job-definition-button\""), "Expected the start-job action from the definition block's markup (issue #679)")
  }

  @Test
  fun `schedule endpoint sets a valid cron expression and the notify-on-slack flag`() {
    val (definitionId, connectionName, _) = createConfiguredDefinition()

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("schedule", "0 0 3 * * ?")
      .formParam("notifyOnSlack", "on")
      .`when`()
      .post("/ui/user/imports/definitions/$definitionId/schedule")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val row = definitionRow(connectionName)
    assertTrue(row.contains("0 0 3 * * ?"), "Expected the cron expression to be rendered")
    assertTrue(row.contains("""data-definition-notify-on-slack="true""""), "Expected notifyOnSlack to be persisted")
  }

  @Test
  fun `schedule endpoint rejects an invalid cron expression`() {
    val (definitionId, _, _) = createConfiguredDefinition()

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("schedule", "not a cron")
      .`when`()
      .post("/ui/user/imports/definitions/$definitionId/schedule")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `schedule endpoint clears the schedule when given a blank value`() {
    val (definitionId, connectionName, _) = createConfiguredDefinition()
    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("schedule", "0 0 3 * * ?")
      .`when`()
      .post("/ui/user/imports/definitions/$definitionId/schedule")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("schedule", " ")
      .`when`()
      .post("/ui/user/imports/definitions/$definitionId/schedule")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val row = definitionRow(connectionName)
    assertTrue(row.contains("Manuell"), "Expected the definition to fall back to the manual label once its schedule is cleared")
  }

  @Test
  fun `schedule endpoint reports an error for a definition without a mapping configured yet`() {
    val app = installAppWithMandatoryStringProperty()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"items":[{"name":"Alice"}]}""".right())
    val connectionName = createConnection()
    triggerImportAndGetId(app.installedAppId, connectionName, app.entityId)
    val definitionId = definitionIdForConnection(connectionName)

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("schedule", "0 0 3 * * ?")
      .`when`()
      .post("/ui/user/imports/definitions/$definitionId/schedule")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `delete endpoint removes the definition from the table`() {
    val (definitionId, connectionName, _) = createConfiguredDefinition()

    given()
      .`when`()
      .post("/ui/user/imports/definitions/$definitionId/delete")
      .then()
      .statusCode(200)
      .body("ok", equalTo(true))

    val tableHtml = given().`when`().get("/ui/user/imports/table").then().statusCode(200).extract().body().asString()
    assertTrue(!tableHtml.contains(connectionName), "Expected the deleted definition to no longer appear in the table")
  }

  @Test
  fun `delete endpoint reports an error for an unknown definition id`() {
    given()
      .`when`()
      .post("/ui/user/imports/definitions/unknown-definition/delete")
      .then()
      .statusCode(200)
      .body("ok", equalTo(false))
  }

  @Test
  fun `merged imports page lists a configured definition's row directly, without a separate definitions overview link`() {
    val (_, connectionName, _) = createConfiguredDefinition()

    val html = given().`when`().get("/ui/user/imports").then().statusCode(200).extract().body().asString()

    assertTrue(html.contains(connectionName), "Expected the definition's connection name to appear directly on the merged imports page")
    assertTrue(!html.contains("data-testid=\"import-definitions-link\""), "Expected no separate link to a definitions overview since the pages are merged")
  }

  @Test
  fun `history endpoint lists an accepted job's run and hides it from the main definition row`() {
    val app = installAppWithMandatoryStringProperty()
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn("""{"items":[{"name":"Alice"}]}""".right())
    val connectionName = createConnection()
    val importId = triggerImportAndGetId(app.installedAppId, connectionName, app.entityId)
    saveMapping(importId, app.propertyId)
    val definitionId = definitionIdForConnection(connectionName)

    given().`when`().post("/ui/user/imports/$importId/dry-run/accept").then().statusCode(200).body("ok", equalTo(true))
    awaitImportAccepted(importId)

    val historyHtml = given().`when`().get("/ui/user/imports/definitions/$definitionId/history").then().statusCode(200).extract().body().asString()
    assertTrue(historyHtml.contains("data-testid=\"history-row\""), "Expected the accepted job to appear as a history row")

    val row = definitionRow(connectionName)
    assertTrue(!row.contains("data-testid=\"import-job-link\""), "Expected the accepted job to no longer be listed as an in-progress job on the main row")
  }

  @Test
  fun `history endpoint returns no rows for an unowned or unknown definition id, mirroring the run and delete endpoints' ownership check`() {
    val html = given()
      .`when`()
      .get("/ui/user/imports/definitions/unknown-definition/history")
      .then()
      .statusCode(200)
      .extract().body().asString()

    assertTrue(!html.contains("data-testid=\"history-row\""), "Expected no history rows for a definition id this user does not own")
  }

  private fun awaitImportAccepted(importJobId: String) {
    val deadlineMs = System.currentTimeMillis() + 5000
    while (System.currentTimeMillis() < deadlineMs) {
      val tableHtml = given().`when`().get("/ui/user/imports/table").then().statusCode(200).extract().body().asString()
      if (!tableHtml.contains("data-import-id=\"$importJobId\"")) return
      Thread.sleep(50)
    }
    throw AssertionError("Expected import job $importJobId to be accepted by the outbox dispatcher within 5s")
  }
}
