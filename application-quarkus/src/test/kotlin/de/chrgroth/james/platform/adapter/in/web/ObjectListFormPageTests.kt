package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.decodeListValue
import de.chrgroth.james.platform.domain.model.app.decodeObjectValue
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.`in`.app.AppManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.AppVersionManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** The create and edit forms of an entity with a LIST of OBJECTs (repeatable groups of fields, e.g. swimming sessions). */
@QuarkusTest
@TestSecurity(user = ObjectListFormPageTests.USERNAME, roles = ["DEVELOPER", "USER"])
class ObjectListFormPageTests {

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @Inject
  lateinit var appManagement: AppManagementPort

  @Inject
  lateinit var appVersionManagement: AppVersionManagementPort

  @Inject
  lateinit var userAppStore: UserAppStorePort

  @Inject
  lateinit var appDataRepository: AppDataRepositoryPort

  private data class SwimApp(val installedAppId: String, val entityId: String, val sessionsId: String, val laneLengthId: String, val lanesId: String)

  private fun createSwimApp(): SwimApp {
    val user = userRepository.findByUsername(Username(USERNAME)) ?: User(
      id = UserId(UUID.randomUUID().toString()),
      username = Username(USERNAME),
      passwordHash = "hash",
      roles = setOf(UserRole.DEVELOPER, UserRole.USER),
      createdAt = Instant.now(),
    ).also { userRepository.save(it) }
    val appId = appManagement.createApp("Swim ${UUID.randomUUID()}", null, user.id.value).getOrNull()!!.id.value
    val versionId = appVersionManagement.createVersion(appId).getOrNull()!!.id.value
    val entityId = appVersionManagement.addEntity(appId, versionId, "Schwimmen").getOrNull()!!.entityDefinitions.single().id.value
    val sessionsId = appVersionManagement.addProperty(appId, versionId, entityId, "sessions", "LIST", nullable = false, listItemType = "OBJECT").getOrNull()!!
      .entityDefinitions.single().properties.single().id.value
    val laneLengthId = appVersionManagement.addProperty(appId, versionId, entityId, "bahnlänge", "LONG", nullable = false, path = listOf(sessionsId)).getOrNull()!!
      .entityDefinitions.single().properties.single().nestedProperties.single().id.value
    appVersionManagement.setPropertyUnit(appId, versionId, entityId, laneLengthId, "DISTANCE", "METERS", "METERS", listOf(sessionsId)).getOrNull()!!
    appVersionManagement.setPropertyDefault(appId, versionId, entityId, laneLengthId, "25", listOf(sessionsId)).getOrNull()!!
    val lanesId = appVersionManagement.addProperty(appId, versionId, entityId, "bahnen", "LONG", nullable = false, path = listOf(sessionsId)).getOrNull()!!
      .entityDefinitions.single().properties.single().nestedProperties.last().id.value
    appVersionManagement.publishVersion(appId, "BUGFIX", "Initial release").getOrNull()!!
    val installedAppId = userAppStore.installApp(USERNAME, appId).getOrNull()!!.id.value
    return SwimApp(installedAppId, entityId, sessionsId, laneLengthId, lanesId)
  }

  @Test
  fun `new data page renders a LIST of OBJECTs as object fields with the item fields and their defaults`() {
    val app = createSwimApp()

    val html = given().`when`().get("/ui/user/apps/${app.installedAppId}/data/new?entityId=${app.entityId}").then().statusCode(200).extract().body().asString()

    assertThat(html).contains("""class="object-property-fields" data-property-id="${app.sessionsId}"""")
    val fieldsJson = Regex("""<script type="application/json" id="object-fields-data">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).find(html)!!.groupValues[1]
    assertThat(fieldsJson).contains("\"listItemType\":\"OBJECT\"").contains(app.laneLengthId).contains("\"defaultValue\":\"25\"")
  }

  @Test
  fun `saving a LIST of OBJECTs stores its items and the edit page prefills them`() {
    val app = createSwimApp()

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("entityTypeId", app.entityId)
      .formParam("prop_${app.sessionsId}.${app.laneLengthId}", "25", "50")
      .formParam("prop_${app.sessionsId}.${app.lanesId}", "40", "20")
      .`when`()
      .post("/ui/user/apps/${app.installedAppId}/data")
      .then()
      .statusCode(200)
      .body(containsString("\"ok\":true"))

    val stored = appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId(app.installedAppId), EntityDefinitionId(app.entityId)).single()
    val items = decodeListValue(stored.data[app.sessionsId]).map { decodeObjectValue(it) }
    assertThat(items).containsExactly(mapOf(app.laneLengthId to "25", app.lanesId to "40"), mapOf(app.laneLengthId to "50", app.lanesId to "20"))

    val html = given().`when`().get("/ui/user/apps/${app.installedAppId}/data/${stored.id.value}").then().statusCode(200).extract().body().asString()
    assertThat(html).contains("""data-property-id="${app.sessionsId}"""")
    assertThat(html).contains("""{"${app.sessionsId}":[{"${app.laneLengthId}":"25","${app.lanesId}":"40"},{"${app.laneLengthId}":"50","${app.lanesId}":"20"}]}""")
  }

  @Test
  fun `saving a LIST of OBJECTs reports an item missing a required field`() {
    val app = createSwimApp()

    given()
      .contentType("application/x-www-form-urlencoded")
      .formParam("entityTypeId", app.entityId)
      .formParam("prop_${app.sessionsId}.${app.laneLengthId}", "25")
      .formParam("prop_${app.sessionsId}.${app.lanesId}", "")
      .`when`()
      .post("/ui/user/apps/${app.installedAppId}/data")
      .then()
      .body(containsString("\"ok\":false"))
  }

  companion object {
    const val USERNAME = "test-object-list-user"
  }
}
