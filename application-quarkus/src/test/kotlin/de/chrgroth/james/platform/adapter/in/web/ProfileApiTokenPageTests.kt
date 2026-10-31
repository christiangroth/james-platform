package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.user.ApiTokenScope
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.`in`.user.ApiTokenPort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

private const val DEVELOPER_USERNAME = "test-profile-api-token-dev"
private const val ADMIN_USERNAME = "test-profile-api-token-admin"

@QuarkusTest
@TestSecurity(user = DEVELOPER_USERNAME, roles = ["USER", "DEVELOPER"])
class ProfileApiTokenPageTests {

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @Inject
  lateinit var apiTokens: ApiTokenPort

  @BeforeEach
  fun setup() {
    if (userRepository.findByUsername(Username(DEVELOPER_USERNAME)) == null) {
      userRepository.save(
        User(
          id = UserId(UUID.randomUUID().toString()),
          username = Username(DEVELOPER_USERNAME),
          passwordHash = "hash",
          roles = setOf(UserRole.USER, UserRole.DEVELOPER),
          createdAt = Instant.now(),
        ),
      )
    }
  }

  @Test
  fun `profile page shows the api token section with all scopes the user has`() {
    given()
      .`when`()
      .get("/ui/profile")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="api-tokens-card""""))
      .body(containsString("""action="/ui/profile/api-tokens""""))
      .body(containsString("""data-testid="api-token-scope-USER_READ""""))
      .body(containsString("""data-testid="api-token-scope-DEVELOPER_RELEASE""""))
      .body(not(containsString("""data-testid="api-token-created-notice"""")))
  }

  @Test
  fun `creating a token shows the plain token once and lists it afterwards`() {
    val name = "agent-${UUID.randomUUID()}"
    val html = given()
      .redirects().follow(false)
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", name)
      .formParam("scopes", "USER_READ")
      .formParam("scopes", "DEVELOPER_WRITE")
      .`when`()
      .post("/ui/profile/api-tokens")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="api-token-created-notice""""))
      .body(containsString("API-Token erstellt."))
      .extract().body().asString()

    val plainToken = Regex("""data-testid="api-token-created-value">(jp_[A-Za-z0-9_-]+)<""").find(html)?.groupValues?.get(1)
    assertThat(plainToken).isNotNull()
    val token = apiTokens.listTokens(DEVELOPER_USERNAME).getOrNull()!!.single { it.name == name }
    assertThat(token.scopes).containsExactlyInAnyOrder(ApiTokenScope.USER_READ, ApiTokenScope.DEVELOPER_WRITE)
    assertThat(apiTokens.authenticate(plainToken!!)?.token?.id).isEqualTo(token.id)

    given()
      .`when`()
      .get("/ui/profile")
      .then()
      .statusCode(200)
      .body(containsString(name))
      .body(containsString(token.tokenPrefix))
      .body(not(containsString(plainToken)))
  }

  @Test
  fun `creating a token without name or scope shows an error`() {
    given()
      .redirects().follow(false)
      .contentType("application/x-www-form-urlencoded")
      .formParam("name", "no scopes")
      .`when`()
      .post("/ui/profile/api-tokens")
      .then()
      .statusCode(303)
      .header("Location", containsString("/ui/profile/error?error=API-TOKEN-002"))

    given()
      .`when`()
      .get("/ui/profile/error?error=API-TOKEN-002")
      .then()
      .statusCode(200)
      .body(containsString("Bitte mindestens eine Berechtigung auswählen."))
  }

  @Test
  fun `token can be deactivated, activated and deleted from the profile page`() {
    val tokenId = apiTokens.createToken(DEVELOPER_USERNAME, "toggle-${UUID.randomUUID()}", setOf(ApiTokenScope.USER_READ), emptySet())
      .getOrNull()!!.token.id

    given().redirects().follow(false).`when`().post("/ui/profile/api-tokens/${tokenId.value}/deactivate")
      .then().statusCode(303).header("Location", containsString("/ui/profile/success?msg=api-token-deactivated"))
    assertThat(apiTokens.listTokens(DEVELOPER_USERNAME).getOrNull()!!.single { it.id == tokenId }.active).isFalse()

    given().redirects().follow(false).`when`().post("/ui/profile/api-tokens/${tokenId.value}/activate")
      .then().statusCode(303).header("Location", containsString("/ui/profile/success?msg=api-token-activated"))
    assertThat(apiTokens.listTokens(DEVELOPER_USERNAME).getOrNull()!!.single { it.id == tokenId }.active).isTrue()

    given().redirects().follow(false).`when`().post("/ui/profile/api-tokens/${tokenId.value}/delete")
      .then().statusCode(303).header("Location", containsString("/ui/profile/success?msg=api-token-deleted"))
    assertThat(apiTokens.listTokens(DEVELOPER_USERNAME).getOrNull()!!.none { it.id == tokenId }).isTrue()
  }

  @Test
  fun `tokens of other users cannot be managed`() {
    val other = User(
      id = UserId(UUID.randomUUID().toString()),
      username = Username("test-profile-api-token-other-${UUID.randomUUID()}"),
      passwordHash = "hash",
      roles = setOf(UserRole.USER),
      createdAt = Instant.now(),
    )
    userRepository.save(other)
    val foreignId = apiTokens.createToken(other.username.value, "foreign", setOf(ApiTokenScope.USER_READ), emptySet()).getOrNull()!!.token.id

    given().redirects().follow(false).`when`().post("/ui/profile/api-tokens/${foreignId.value}/deactivate")
      .then().statusCode(303).header("Location", containsString("/ui/profile/error?error=API-TOKEN-004"))
    given().redirects().follow(false).`when`().post("/ui/profile/api-tokens/${foreignId.value}/delete")
      .then().statusCode(303).header("Location", containsString("/ui/profile/error?error=API-TOKEN-004"))
    assertThat(apiTokens.listTokens(other.username.value).getOrNull()!!.single().active).isTrue()
  }
}

@QuarkusTest
@TestSecurity(user = ADMIN_USERNAME, roles = ["ADMIN"])
class ProfileApiTokenAdminPageTests {

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @Test
  fun `admin without user or developer role sees no api token section`() {
    if (userRepository.findByUsername(Username(ADMIN_USERNAME)) == null) {
      userRepository.save(
        User(
          id = UserId(UUID.randomUUID().toString()),
          username = Username(ADMIN_USERNAME),
          passwordHash = "hash",
          roles = setOf(UserRole.ADMIN),
          createdAt = Instant.now(),
        ),
      )
    }

    given()
      .`when`()
      .get("/ui/profile")
      .then()
      .statusCode(200)
      .body(not(containsString("""data-testid="api-tokens-card"""")))
  }
}
