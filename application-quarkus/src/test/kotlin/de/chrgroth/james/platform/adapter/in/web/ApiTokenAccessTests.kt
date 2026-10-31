package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.user.ApiTokenId
import de.chrgroth.james.platform.domain.model.user.ApiTokenScope
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.`in`.app.AppManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.AppVersionManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import de.chrgroth.james.platform.domain.port.`in`.user.ApiTokenPort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.not
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@QuarkusTest
class ApiTokenAccessTests {

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
      username = Username("api-token-${UUID.randomUUID()}"),
      passwordHash = "hash",
      roles = roles.toSet(),
      createdAt = Instant.now(),
    )
    userRepository.save(user)
    return user
  }

  private fun createToken(user: User, vararg scopes: ApiTokenScope, appIds: Set<String> = emptySet()): Pair<String, String> {
    val created = apiTokens.createToken(user.username.value, "test", scopes.toSet(), appIds).getOrNull()!!
    return created.token.id.value to created.plainToken
  }

  private fun bearer(token: String): RequestSpecification = given().header("Authorization", "Bearer $token").redirects().follow(false)

  private data class PublishedApp(val appId: String, val entityId: String, val propertyId: String, val listPropertyId: String)

  /** Creates an App owned by [developer] with one entity ("Item": STRING "Name", LIST "Tags") and publishes it. */
  private fun createPublishedApp(developer: User): PublishedApp {
    val appId = appManagement.createApp("API App ${UUID.randomUUID()}", null, developer.id.value).getOrNull()!!.id.value
    val versionId = appVersionManagement.createVersion(appId).getOrNull()!!.id.value
    val entityId = appVersionManagement.addEntity(appId, versionId, "Item").getOrNull()!!.entityDefinitions.single().id.value
    appVersionManagement.addProperty(appId, versionId, entityId, "Name", "STRING", nullable = true).getOrNull()!!
    val withList = appVersionManagement.addProperty(appId, versionId, entityId, "Tags", "LIST", nullable = true, listItemType = "STRING").getOrNull()!!
    val properties = withList.entityDefinitions.single().properties
    appVersionManagement.publishVersion(appId, "BUGFIX", "Initial release").getOrNull()!!
    return PublishedApp(appId, entityId, properties.first { it.name == "Name" }.id.value, properties.first { it.name == "Tags" }.id.value)
  }

  private fun install(user: User, appId: String): String = userAppStore.installApp(user.username.value, appId).getOrNull()!!.id.value

  // endregion

  // region authentication and access boundaries

  @Test
  fun `api requires a valid bearer token`() {
    given().redirects().follow(false).`when`().get("/api/v1/token").then().statusCode(401)
    bearer("jp_unknown").`when`().get("/api/v1/token").then().statusCode(401)
    bearer("not-even-our-format").`when`().get("/api/v1/user/apps").then().statusCode(401)
  }

  @Test
  fun `token info returns owner and effective scopes including implied ones`() {
    val user = createUser(UserRole.USER, UserRole.DEVELOPER)
    val (tokenId, token) = createToken(user, ApiTokenScope.USER_WRITE, ApiTokenScope.DEVELOPER_RELEASE)

    bearer(token).`when`().get("/api/v1/token")
      .then()
      .statusCode(200)
      .body("username", equalTo(user.username.value))
      .body("tokenId", equalTo(tokenId))
      .body("scopes", equalTo(listOf("DEVELOPER_READ", "DEVELOPER_RELEASE", "USER_READ", "USER_WRITE")))
  }

  @Test
  fun `token is rejected outside the api, including ui and token management`() {
    val user = createUser(UserRole.USER)
    val (_, token) = createToken(user, ApiTokenScope.USER_WRITE)

    bearer(token).`when`().get("/ui/profile").then().statusCode(403)
    bearer(token).`when`().get("/ui/user/dashboard").then().statusCode(403)
    bearer(token).contentType("application/x-www-form-urlencoded").formParam("name", "x").formParam("scopes", "USER_WRITE")
      .`when`().post("/ui/profile/api-tokens").then().statusCode(403)
    bearer(token).`when`().get("/api/../ui/profile").then().statusCode(403)
    assertThat(apiTokens.listTokens(user.username.value).getOrNull()).hasSize(1)
  }

  @Test
  @TestSecurity(user = "api-token-cookie-user", roles = ["USER", "DEVELOPER"])
  fun `a cookie session cannot use the token api`() {
    given().redirects().follow(false).`when`().get("/api/v1/user/apps").then().statusCode(403)
    given().redirects().follow(false).`when`().get("/api/v1/developer/apps").then().statusCode(403)
  }

  @Test
  fun `deactivated token is rejected until it is activated again`() {
    val user = createUser(UserRole.USER)
    val (tokenId, token) = createToken(user, ApiTokenScope.USER_READ)
    bearer(token).`when`().get("/api/v1/user/apps").then().statusCode(200)

    apiTokens.setActive(user.username.value, ApiTokenId(tokenId), false)
    bearer(token).`when`().get("/api/v1/user/apps").then().statusCode(401)

    apiTokens.setActive(user.username.value, ApiTokenId(tokenId), true)
    bearer(token).`when`().get("/api/v1/user/apps").then().statusCode(200)
  }

  @Test
  fun `token loses scopes whose role the user lost and is rejected without any`() {
    val user = createUser(UserRole.USER, UserRole.DEVELOPER)
    val (_, token) = createToken(user, ApiTokenScope.DEVELOPER_READ)
    bearer(token).`when`().get("/api/v1/developer/apps").then().statusCode(200)

    userRepository.save(user.copy(roles = setOf(UserRole.USER)))
    bearer(token).`when`().get("/api/v1/developer/apps").then().statusCode(401)
  }

  @Test
  fun `token of a deactivated user is rejected`() {
    val user = createUser(UserRole.USER)
    val (_, token) = createToken(user, ApiTokenScope.USER_READ)

    userRepository.save(user.copy(active = false))
    bearer(token).`when`().get("/api/v1/user/apps").then().statusCode(401)
  }

  // endregion

  // region user scopes

  @Test
  fun `user read scope lists installed apps with structure and data but cannot write`() {
    val developer = createUser(UserRole.USER, UserRole.DEVELOPER)
    val app = createPublishedApp(developer)
    val user = createUser(UserRole.USER)
    val installedAppId = install(user, app.appId)
    val (_, writeToken) = createToken(user, ApiTokenScope.USER_WRITE)
    val (_, readToken) = createToken(user, ApiTokenScope.USER_READ)

    val dataId = bearer(writeToken).contentType("application/json")
      .body(mapOf("entityTypeId" to app.entityId, "values" to mapOf(app.propertyId to "Hello", app.listPropertyId to listOf("a", "b"))))
      .`when`().post("/api/v1/user/apps/$installedAppId/data")
      .then()
      .statusCode(201)
      .body("values.${app.propertyId}", equalTo("Hello"))
      .body("values.${app.listPropertyId}", equalTo(listOf("a", "b")))
      .extract().path<String>("id")

    bearer(readToken).`when`().get("/api/v1/user/apps")
      .then().statusCode(200).body("installedAppId", hasItem(installedAppId))
    bearer(readToken).`when`().get("/api/v1/user/apps/$installedAppId")
      .then().statusCode(200)
      .body("installedApp.appId", equalTo(app.appId))
      .body("version.entities[0].properties.name", equalTo(listOf("Name", "Tags")))
    bearer(readToken).`when`().get("/api/v1/user/apps/$installedAppId/data?entityTypeId=${app.entityId}")
      .then().statusCode(200).body("id", equalTo(listOf(dataId)))
    bearer(readToken).`when`().get("/api/v1/user/apps/$installedAppId/data/$dataId")
      .then().statusCode(200).body("values.${app.propertyId}", equalTo("Hello"))

    bearer(readToken).contentType("application/json").body(mapOf("entityTypeId" to app.entityId, "values" to emptyMap<String, Any>()))
      .`when`().post("/api/v1/user/apps/$installedAppId/data").then().statusCode(403)
    bearer(readToken).`when`().delete("/api/v1/user/apps/$installedAppId/data/$dataId").then().statusCode(403)
  }

  @Test
  fun `user write scope updates and deletes data and reports validation errors`() {
    val developer = createUser(UserRole.USER, UserRole.DEVELOPER)
    val app = createPublishedApp(developer)
    val user = createUser(UserRole.USER)
    val installedAppId = install(user, app.appId)
    val (_, token) = createToken(user, ApiTokenScope.USER_WRITE)

    bearer(token).contentType("application/json").body(mapOf("values" to emptyMap<String, Any>()))
      .`when`().post("/api/v1/user/apps/$installedAppId/data").then().statusCode(400)
    bearer(token).contentType("application/json").body(mapOf("entityTypeId" to "unknown-entity", "values" to emptyMap<String, Any>()))
      .`when`().post("/api/v1/user/apps/$installedAppId/data").then().statusCode(404).body("code", equalTo("APPDATA-002"))

    val dataId = bearer(token).contentType("application/json")
      .body(mapOf("entityTypeId" to app.entityId, "values" to mapOf(app.propertyId to "Before")))
      .`when`().post("/api/v1/user/apps/$installedAppId/data")
      .then().statusCode(201).extract().path<String>("id")

    bearer(token).contentType("application/json").body(mapOf("values" to mapOf(app.propertyId to "After")))
      .`when`().put("/api/v1/user/apps/$installedAppId/data/$dataId")
      .then().statusCode(200).body("values.${app.propertyId}", equalTo("After"))

    bearer(token).`when`().delete("/api/v1/user/apps/$installedAppId/data/$dataId").then().statusCode(204)
    bearer(token).`when`().get("/api/v1/user/apps/$installedAppId/data/$dataId").then().statusCode(404)
  }

  @Test
  fun `user scopes restricted to an app hide all other installations`() {
    val developer = createUser(UserRole.USER, UserRole.DEVELOPER)
    val allowed = createPublishedApp(developer)
    val other = createPublishedApp(developer)
    val user = createUser(UserRole.USER)
    val allowedInstallation = install(user, allowed.appId)
    val otherInstallation = install(user, other.appId)
    val (_, token) = createToken(user, ApiTokenScope.USER_WRITE, appIds = setOf(allowed.appId))

    bearer(token).`when`().get("/api/v1/user/apps")
      .then().statusCode(200).body("installedAppId", equalTo(listOf(allowedInstallation)))
    bearer(token).`when`().get("/api/v1/user/apps/$allowedInstallation").then().statusCode(200)
    bearer(token).`when`().get("/api/v1/user/apps/$otherInstallation").then().statusCode(404)
    bearer(token).`when`().get("/api/v1/user/apps/$otherInstallation/data").then().statusCode(404)
    bearer(token).contentType("application/json").body(mapOf("entityTypeId" to other.entityId, "values" to mapOf(other.propertyId to "x")))
      .`when`().post("/api/v1/user/apps/$otherInstallation/data").then().statusCode(404)
  }

  @Test
  fun `user scopes never reach installations of other users`() {
    val developer = createUser(UserRole.USER, UserRole.DEVELOPER)
    val app = createPublishedApp(developer)
    val owner = createUser(UserRole.USER)
    val installedAppId = install(owner, app.appId)
    val (_, foreignToken) = createToken(createUser(UserRole.USER), ApiTokenScope.USER_WRITE)

    bearer(foreignToken).`when`().get("/api/v1/user/apps").then().statusCode(200).body("installedAppId", not(hasItem(installedAppId)))
    bearer(foreignToken).`when`().get("/api/v1/user/apps/$installedAppId").then().statusCode(404)
  }

  // endregion

  // region developer scopes

  @Test
  fun `developer read scope lists own apps with all versions including drafts but cannot write or release`() {
    val developer = createUser(UserRole.USER, UserRole.DEVELOPER)
    val app = createPublishedApp(developer)
    val draftId = appVersionManagement.createVersion(app.appId).getOrNull()!!.id.value
    val foreignApp = createPublishedApp(createUser(UserRole.DEVELOPER))
    val (_, token) = createToken(developer, ApiTokenScope.DEVELOPER_READ)

    bearer(token).`when`().get("/api/v1/developer/apps").then().statusCode(200).body("id", equalTo(listOf(app.appId)))
    bearer(token).`when`().get("/api/v1/developer/apps/${app.appId}")
      .then().statusCode(200).body("app.id", equalTo(app.appId)).body("versions.status", hasItem("DRAFT")).body("versions", hasSize<Any>(2))
    bearer(token).`when`().get("/api/v1/developer/apps/${app.appId}/versions")
      .then().statusCode(200).body("status", hasItem("DRAFT")).body("status", hasItem("PUBLISHED"))
    bearer(token).`when`().get("/api/v1/developer/apps/${app.appId}/versions/$draftId")
      .then().statusCode(200).body("status", equalTo("DRAFT")).body("entities[0].properties", hasSize<Any>(2))
    bearer(token).`when`().get("/api/v1/developer/apps/${app.appId}/versions/$draftId/version-bump").then().statusCode(200)

    bearer(token).`when`().get("/api/v1/developer/apps/${foreignApp.appId}").then().statusCode(404)
    bearer(token).`when`().get("/api/v1/developer/apps/${foreignApp.appId}/versions").then().statusCode(404)

    bearer(token).`when`().delete("/api/v1/developer/apps/${app.appId}/versions/$draftId").then().statusCode(403)
    bearer(token).contentType("application/json").body(mapOf("name" to "X"))
      .`when`().post("/api/v1/developer/apps/${app.appId}/versions/$draftId/entities").then().statusCode(403)
    bearer(token).contentType("application/json").body(mapOf("bumpType" to "FEATURE", "releaseNotes" to "x"))
      .`when`().post("/api/v1/developer/apps/${app.appId}/versions/publish").then().statusCode(403)
  }

  @Test
  fun `developer write scope creates, edits and deletes drafts but cannot release`() {
    val developer = createUser(UserRole.USER, UserRole.DEVELOPER)
    val app = createPublishedApp(developer)
    val (_, token) = createToken(developer, ApiTokenScope.DEVELOPER_WRITE)
    val base = "/api/v1/developer/apps/${app.appId}/versions"

    val draftId = bearer(token).`when`().post(base).then().statusCode(201).body("status", equalTo("DRAFT")).extract().path<String>("id")
    bearer(token).`when`().post(base).then().statusCode(400).body("code", equalTo("APPVER-007"))

    val entityId = bearer(token).contentType("application/json").body(mapOf("name" to "Note"))
      .`when`().post("$base/$draftId/entities")
      .then().statusCode(200).body("entities.name", hasItem("Note"))
      .extract().path<String>("entities.find { it.name == 'Note' }.id")
    val propertyId = bearer(token).contentType("application/json").body(mapOf("name" to "Count", "type" to "LONG", "nullable" to false))
      .`when`().post("$base/$draftId/entities/$entityId/properties")
      .then().statusCode(200)
      .extract().path<String>("entities.find { it.name == 'Note' }.properties[0].id")
    bearer(token).contentType("application/json")
      .body(mapOf("constraints" to listOf(mapOf("type" to "MIN_LONG", "value" to "1"), mapOf("type" to "MAX_LONG", "value" to "10"))))
      .`when`().put("$base/$draftId/entities/$entityId/properties/$propertyId/constraints")
      .then().statusCode(200)
      .body("entities.find { it.name == 'Note' }.properties[0].constraints.type", equalTo(listOf("MIN_LONG", "MAX_LONG")))
    bearer(token).contentType("application/json").body(mapOf("constraints" to listOf(mapOf("type" to "MIN_LONG", "value" to "abc"))))
      .`when`().put("$base/$draftId/entities/$entityId/properties/$propertyId/constraints").then().statusCode(400)
    bearer(token).contentType("application/json").body(mapOf("value" to "5"))
      .`when`().put("$base/$draftId/entities/$entityId/properties/$propertyId/default")
      .then().statusCode(200).body("entities.find { it.name == 'Note' }.properties[0].default", equalTo("5"))
    bearer(token).contentType("application/json").body(mapOf("name" to "Overview"))
      .`when`().post("$base/$draftId/reports").then().statusCode(200).body("reports.name", hasItem("Overview"))

    bearer(token).contentType("application/json").body(mapOf("bumpType" to "FEATURE", "releaseNotes" to "x"))
      .`when`().post("$base/publish").then().statusCode(403)

    bearer(token).`when`().delete("$base/$draftId/entities/$entityId")
      .then().statusCode(200).body("entities.name", not(hasItem("Note")))
    bearer(token).`when`().delete("$base/$draftId").then().statusCode(204)
    bearer(token).`when`().get("$base/$draftId").then().statusCode(404)
  }

  @Test
  fun `developer write scope cannot edit published versions or foreign apps`() {
    val developer = createUser(UserRole.USER, UserRole.DEVELOPER)
    val app = createPublishedApp(developer)
    val publishedId = appVersionManagement.listVersions(app.appId).getOrNull()!!.single().id.value
    val foreignApp = createPublishedApp(createUser(UserRole.DEVELOPER))
    val (_, token) = createToken(developer, ApiTokenScope.DEVELOPER_WRITE)

    bearer(token).contentType("application/json").body(mapOf("name" to "X"))
      .`when`().post("/api/v1/developer/apps/${app.appId}/versions/$publishedId/entities")
      .then().statusCode(404).body("code", equalTo("APPVER-002"))
    assertThat(appVersionManagement.getVersion(app.appId, publishedId).getOrNull()!!.entityDefinitions).hasSize(1)
    bearer(token).`when`().post("/api/v1/developer/apps/${foreignApp.appId}/versions").then().statusCode(404)
    assertThat(appVersionManagement.listVersions(foreignApp.appId).getOrNull()).hasSize(1)
  }

  @Test
  fun `developer release scope publishes the draft`() {
    val developer = createUser(UserRole.USER, UserRole.DEVELOPER)
    val app = createPublishedApp(developer)
    val draftId = appVersionManagement.createVersion(app.appId).getOrNull()!!.id.value
    appVersionManagement.addReport(app.appId, draftId, "Overview").getOrNull()!!
    val (_, token) = createToken(developer, ApiTokenScope.DEVELOPER_RELEASE)
    val base = "/api/v1/developer/apps/${app.appId}/versions"

    bearer(token).contentType("application/json").body(mapOf("bumpType" to "FEATURE", "releaseNotes" to " "))
      .`when`().post("$base/publish").then().statusCode(400)
    bearer(token).contentType("application/json").body(mapOf("bumpType" to "FEATURE", "releaseNotes" to "Released via API"))
      .`when`().post("$base/publish")
      .then().statusCode(200)
      .body("id", equalTo(draftId))
      .body("status", equalTo("PUBLISHED"))
      .body("releaseNotes", equalTo("Released via API"))
    bearer(token).`when`().post(base).then().statusCode(403)
  }

  // endregion
}
