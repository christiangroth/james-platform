package de.chrgroth.james.platform.adapter.`in`.web

import arrow.core.right
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.user.ApiTokenScope
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.`in`.app.AppManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.AppVersionManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import de.chrgroth.james.platform.domain.port.`in`.user.ApiTokenPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportFetchPort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Instant
import java.util.UUID

/** Installing Apps (`USER_INSTALL`), imports (`USER_IMPORT`, accept with `USER_WRITE`) and creating Apps (`DEVELOPER_WRITE`) via the token API. */
@QuarkusTest
class ApiTokenImportAndInstallTests {

  @InjectMock
  lateinit var importFetch: ImportFetchPort

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @Inject
  lateinit var apiTokens: ApiTokenPort

  @Inject
  lateinit var appManagement: AppManagementPort

  @Inject
  lateinit var appVersionManagement: AppVersionManagementPort

  @Inject
  lateinit var userAppStore: UserAppStorePort

  // region setup helpers

  private fun createUser(vararg roles: UserRole): User {
    val user = User(
      id = UserId(UUID.randomUUID().toString()),
      username = Username("api-import-${UUID.randomUUID()}"),
      passwordHash = "hash",
      roles = roles.toSet(),
      createdAt = Instant.now(),
    )
    userRepository.save(user)
    return user
  }

  private fun createToken(user: User, vararg scopes: ApiTokenScope, appIds: Set<String> = emptySet()): String =
    apiTokens.createToken(user.username.value, "test", scopes.toSet(), appIds).getOrNull()!!.plainToken

  private fun bearer(token: String): RequestSpecification = given().header("Authorization", "Bearer $token").redirects().follow(false)

  private fun json(token: String): RequestSpecification = bearer(token).contentType("application/json")

  private data class PublishedApp(val appId: String, val entityId: String, val namePropertyId: String)

  /** An App with one entity "Person" whose mandatory STRING "Name" must start with an upper case letter (a Pattern, checked in the dry-run). */
  private fun createPublishedApp(developer: User): PublishedApp {
    val appId = appManagement.createApp("Import App ${UUID.randomUUID()}", null, developer.id.value).getOrNull()!!.id.value
    val versionId = appVersionManagement.createVersion(appId).getOrNull()!!.id.value
    val entityId = appVersionManagement.addEntity(appId, versionId, "Person").getOrNull()!!.entityDefinitions.single().id.value
    val propertyId = appVersionManagement.addProperty(appId, versionId, entityId, "Name", "STRING", nullable = false).getOrNull()!!
      .entityDefinitions.single().properties.single().id.value
    appVersionManagement.setPropertyConstraints(appId, versionId, entityId, propertyId, setOf(PropertyConstraint.Pattern("^[A-Z].*"))).getOrNull()!!
    appVersionManagement.publishVersion(appId, "BUGFIX", "Initial release").getOrNull()!!
    return PublishedApp(appId, entityId, propertyId)
  }

  private fun install(user: User, appId: String): String = userAppStore.installApp(user.username.value, appId).getOrNull()!!.id.value

  private data class PreparedJob(val app: PublishedApp, val installedAppId: String, val jobId: String)

  /** Installs a fresh App for [user] and drives an import job via the API up to a saved mapping, with four source records of which one is filtered. */
  private fun prepareMappedJob(user: User, token: String): PreparedJob {
    val app = createPublishedApp(createUser(UserRole.DEVELOPER))
    val installedAppId = install(user, app.appId)
    Mockito.`when`(importFetch.fetch(Mockito.anyString(), Mockito.anyString())).thenReturn(
      """{"items":[{"name":"Alice"},{"name":"bob"},{"name":"Carol"},{"name":"Skip"}]}""".right(),
    )

    val connectionId = json(token)
      .body("""{"name":"james ${UUID.randomUUID()}","baseUrl":"https://james.example.com/api","bearerToken":"source-secret"}""")
      .`when`().post("/api/v1/user/import-connections")
      .then().statusCode(201)
      .body("hasBearerToken", equalTo(true))
      .body("bearerToken", nullValue())
      .extract().path<String>("id")

    val jobId = json(token)
      .body("""{"installedAppId":"$installedAppId","connectionId":"$connectionId","targetEntityDefinitionId":"${app.entityId}","urlPostfix":"/export"}""")
      .`when`().post("/api/v1/user/imports/jobs")
      .then().statusCode(201)
      .body("definition.selectedDataPath", equalTo("items"))
      .body("detectedDataPaths.path", hasItem("items"))
      .extract().path<String>("job.id")

    json(token).body("""{"dataPath":"/items"}""")
      .`when`().put("/api/v1/user/imports/jobs/$jobId/data-path")
      .then().statusCode(200)
      .body("job.status", equalTo("DATA_IDENTIFIED"))
      .body("definition.selectedDataPath", equalTo("items"))
      .body("detectedSchema.path", hasItem("name"))

    json(token).body("""{"rules":[{"mode":"EXCLUDE","sourcePath":"name","operator":"EQUALS","value":"Skip"}]}""")
      .`when`().put("/api/v1/user/imports/jobs/$jobId/filter")
      .then().statusCode(200)
      .body("totalRecordCount", equalTo(4))
      .body("matchingRecordCount", equalTo(3))

    json(token).body("""{"fieldMappings":[{"targetPropertyId":"${app.namePropertyId}","sourcePath":"name"}]}""")
      .`when`().put("/api/v1/user/imports/jobs/$jobId/mapping")
      .then().statusCode(200)
      .body("ready", equalTo(true))
      .body("targetEntity.name", equalTo("Person"))
      .body("issues.type", hasItem("NOT_STATICALLY_VALIDATED"))

    return PreparedJob(app, installedAppId, jobId)
  }

  // endregion

  // region install

  @Test
  fun `install scope installs and upgrades apps, read scope only browses the app store`() {
    val developer = createUser(UserRole.DEVELOPER)
    val app = createPublishedApp(developer)
    val user = createUser(UserRole.USER)
    val readToken = createToken(user, ApiTokenScope.USER_READ)
    val installToken = createToken(user, ApiTokenScope.USER_INSTALL)

    bearer(readToken).`when`().get("/api/v1/user/app-store").then().statusCode(200).body("appId", hasItem(app.appId))
    bearer(readToken).`when`().get("/api/v1/user/app-store/${app.appId}").then().statusCode(200)
      .body("latestVersion.entities[0].name", equalTo("Person"))
      .body("versions", hasSize<Any>(1))
    bearer(readToken).`when`().post("/api/v1/user/app-store/${app.appId}/install").then().statusCode(403)

    val installed = bearer(installToken).`when`().post("/api/v1/user/app-store/${app.appId}/install")
      .then().statusCode(201)
      .body("appId", equalTo(app.appId))
      .body("installedVersionNumber", notNullValue())
      .extract().jsonPath()
    val installedAppId = installed.getString("installedAppId")
    val initialVersion = installed.getString("installedVersionNumber")
    bearer(installToken).`when`().post("/api/v1/user/app-store/${app.appId}/install").then().statusCode(400).body("code", equalTo("STORE-003"))
    bearer(installToken).`when`().get("/api/v1/user/apps").then().statusCode(200).body("installedAppId", hasItem(installedAppId))

    bearer(installToken).`when`().post("/api/v1/user/apps/$installedAppId/upgrade").then().statusCode(400).body("code", equalTo("STORE-006"))
    val draftId = appVersionManagement.createVersion(app.appId).getOrNull()!!.id.value
    appVersionManagement.addEntity(app.appId, draftId, "Address").getOrNull()!!
    appVersionManagement.publishVersion(app.appId, "FEATURE", "Addresses").getOrNull()!!
    bearer(readToken).`when`().post("/api/v1/user/apps/$installedAppId/upgrade").then().statusCode(403)
    val upgraded = bearer(installToken).`when`().post("/api/v1/user/apps/$installedAppId/upgrade")
      .then().statusCode(200)
      .extract().jsonPath()
    assertThat(upgraded.getString("installedVersionNumber")).isEqualTo(upgraded.getString("latestVersionNumber")).isNotEqualTo(initialVersion)
  }

  @Test
  fun `install scope restricted to an app cannot see or install other apps`() {
    val developer = createUser(UserRole.DEVELOPER)
    val allowed = createPublishedApp(developer)
    val other = createPublishedApp(developer)
    val user = createUser(UserRole.USER)
    val token = createToken(user, ApiTokenScope.USER_INSTALL, appIds = setOf(allowed.appId))

    bearer(token).`when`().get("/api/v1/user/app-store").then().statusCode(200)
      .body("appId", hasItem(allowed.appId))
      .body("appId", not(hasItem(other.appId)))
    bearer(token).`when`().get("/api/v1/user/app-store/${other.appId}").then().statusCode(404)
    bearer(token).`when`().post("/api/v1/user/app-store/${other.appId}/install").then().statusCode(404)
    bearer(token).`when`().post("/api/v1/user/app-store/${allowed.appId}/install").then().statusCode(201)
  }

  // endregion

  // region imports

  @Test
  fun `import scope checks source data against an app up to the dry-run without writing data`() {
    val user = createUser(UserRole.USER, UserRole.DATA_IMPORT)
    val token = createToken(user, ApiTokenScope.USER_IMPORT)
    val prepared = prepareMappedJob(user, token)
    val jobPath = "/api/v1/user/imports/jobs/${prepared.jobId}"

    bearer(token).`when`().get("$jobPath/records?index=1").then().statusCode(200)
      .body("total", equalTo(3))
      .body("sourceData.name", equalTo("bob"))
    bearer(token).`when`().get("$jobPath/records?matched=false").then().statusCode(200)
      .body("total", equalTo(1))
      .body("sourceData.name", equalTo("Skip"))
    bearer(token).`when`().get("$jobPath/filter/values?sourcePath=name").then().statusCode(200).body("$", hasItem("Carol"))
    json(token).body("""{"fieldMappings":[{"targetPropertyId":"${prepared.app.namePropertyId}","sourcePath":"name"}]}""")
      .`when`().post("$jobPath/mapping/sample?index=1")
      .then().statusCode(200)
      .body("total", equalTo(3))
      .body("result.issues[0].violationCode", equalTo("PROP-007"))

    bearer(token).`when`().get("$jobPath/dry-run").then().statusCode(200)
      .body("totalCount", equalTo(3))
      .body("validCount", equalTo(2))
      .body("invalidCount", equalTo(1))
      .body("invalidObjects[0].sourceData.name", equalTo("bob"))
      .body("invalidObjects[0].issues[0].type", equalTo("CONSTRAINT_VIOLATED"))
      .body("invalidObjects[0].issues[0].targetPropertyId", equalTo(prepared.app.namePropertyId))
      .body("validObjects.targetData.${prepared.app.namePropertyId}", equalTo(listOf("Alice", "Carol")))
    bearer(token).`when`().get("$jobPath/dry-run?limit=0").then().statusCode(200)
      .body("validCount", equalTo(2))
      .body("validObjects", hasSize<Any>(0))

    bearer(token).`when`().post("$jobPath/accept").then().statusCode(403)
    bearer(token).`when`().get("/api/v1/user/apps/${prepared.installedAppId}/data").then().statusCode(200).body("$", hasSize<Any>(0))
    bearer(token).`when`().get("/api/v1/user/imports/jobs").then().statusCode(200).body("id", hasItem(prepared.jobId))
    bearer(token).`when`().get("/api/v1/user/imports/definitions").then().statusCode(200)
      .body("configured", hasItem(true))
      .body("fieldMappings[0][0].targetPropertyId", equalTo(prepared.app.namePropertyId))
  }

  @Test
  fun `accepting an import requires the write scope as well and saves the valid objects`() {
    val user = createUser(UserRole.USER, UserRole.DATA_IMPORT)
    val token = createToken(user, ApiTokenScope.USER_IMPORT, ApiTokenScope.USER_WRITE)
    val prepared = prepareMappedJob(user, token)
    val jobPath = "/api/v1/user/imports/jobs/${prepared.jobId}"

    bearer(token).`when`().post("$jobPath/accept?mode=SOMETIMES").then().statusCode(400)
    bearer(token).`when`().post("$jobPath/accept").then().statusCode(202).body("status", equalTo("ACCEPTING"))

    val deadline = System.currentTimeMillis() + 5000
    var status = ""
    while (System.currentTimeMillis() < deadline && status != "ACCEPTED") {
      status = bearer(token).`when`().get(jobPath).then().statusCode(200).extract().path("job.status")
      if (status != "ACCEPTED") Thread.sleep(50)
    }
    bearer(token).`when`().get(jobPath).then().statusCode(200)
      .body("job.status", equalTo("ACCEPTED"))
      .body("job.addedCount", equalTo(2))
      .body("job.discardedCount", equalTo(1))
    bearer(token).`when`().get("/api/v1/user/apps/${prepared.installedAppId}/data").then().statusCode(200).body("$", hasSize<Any>(2))
  }

  @Test
  fun `import endpoints need the import scope, validate input and respect the app restriction`() {
    val user = createUser(UserRole.USER, UserRole.DATA_IMPORT)
    val token = createToken(user, ApiTokenScope.USER_IMPORT)
    val prepared = prepareMappedJob(user, token)
    val jobPath = "/api/v1/user/imports/jobs/${prepared.jobId}"

    val writeOnly = createToken(user, ApiTokenScope.USER_WRITE)
    bearer(writeOnly).`when`().get("/api/v1/user/import-connections").then().statusCode(403)
    bearer(writeOnly).`when`().get("$jobPath/dry-run").then().statusCode(403)

    json(token).body("""{"fieldMappings":[{"targetPropertyId":"${prepared.app.namePropertyId}","sourcePath":"name","conversion":"MAGIC"}]}""")
      .`when`().put("$jobPath/mapping").then().statusCode(400).body("code", equalTo("BAD_REQUEST"))
    json(token).body("""{"rules":[{"mode":"KEEP","sourcePath":"name","operator":"EQUALS"}]}""")
      .`when`().put("$jobPath/filter").then().statusCode(400)
    json(token).body("""{"connectionId":"x"}""").`when`().post("/api/v1/user/imports/jobs").then().statusCode(400)
    bearer(token).`when`().get("/api/v1/user/imports/jobs/unknown-job").then().statusCode(404).body("code", equalTo("IMPORT-009"))

    val otherApp = createPublishedApp(createUser(UserRole.DEVELOPER))
    install(user, otherApp.appId)
    val restricted = createToken(user, ApiTokenScope.USER_IMPORT, appIds = setOf(otherApp.appId))
    bearer(restricted).`when`().get(jobPath).then().statusCode(404)
    bearer(restricted).`when`().get("$jobPath/dry-run").then().statusCode(404)
    bearer(restricted).`when`().get("/api/v1/user/imports/jobs").then().statusCode(200).body("id", not(hasItem(prepared.jobId)))
    bearer(restricted).`when`().get("/api/v1/user/imports/definitions").then().statusCode(200).body("$", hasSize<Any>(0))
    json(restricted)
      .body("""{"installedAppId":"${prepared.installedAppId}","connectionId":"x","targetEntityDefinitionId":"${prepared.app.entityId}"}""")
      .`when`().post("/api/v1/user/imports/jobs").then().statusCode(404)

    val foreign = createUser(UserRole.USER, UserRole.DATA_IMPORT)
    bearer(createToken(foreign, ApiTokenScope.USER_IMPORT)).`when`().get(jobPath).then().statusCode(404)
  }

  // endregion

  // region developer apps

  @Test
  fun `developer write scope creates and renames apps, read scope cannot`() {
    val developer = createUser(UserRole.DEVELOPER)
    val readToken = createToken(developer, ApiTokenScope.DEVELOPER_READ)
    val writeToken = createToken(developer, ApiTokenScope.DEVELOPER_WRITE)
    val name = "Migrated App ${UUID.randomUUID()}"

    json(readToken).body("""{"name":"$name"}""").`when`().post("/api/v1/developer/apps").then().statusCode(403)
    val appId = json(writeToken).body("""{"name":"$name","description":"from james"}""")
      .`when`().post("/api/v1/developer/apps")
      .then().statusCode(201)
      .body("name", equalTo(name))
      .body("description", equalTo("from james"))
      .body("status", equalTo("ACTIVE"))
      .extract().path<String>("id")
    json(writeToken).body("""{"name":"$name"}""").`when`().post("/api/v1/developer/apps").then().statusCode(400).body("code", equalTo("APP-002"))
    json(writeToken).body("""{"name":" "}""").`when`().post("/api/v1/developer/apps").then().statusCode(400).body("code", equalTo("APP-003"))

    json(writeToken).body("""{"name":"$name renamed"}""").`when`().put("/api/v1/developer/apps/$appId")
      .then().statusCode(200)
      .body("name", equalTo("$name renamed"))
      .body("description", nullValue())
    bearer(writeToken).`when`().post("/api/v1/developer/apps/$appId/versions").then().statusCode(201)

    val otherDeveloper = createUser(UserRole.DEVELOPER)
    json(createToken(otherDeveloper, ApiTokenScope.DEVELOPER_WRITE)).body("""{"name":"stolen"}""")
      .`when`().put("/api/v1/developer/apps/$appId").then().statusCode(404)
  }

  // endregion
}
