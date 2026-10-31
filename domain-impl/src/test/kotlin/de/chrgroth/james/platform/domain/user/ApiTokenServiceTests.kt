package de.chrgroth.james.platform.domain.user

import de.chrgroth.james.platform.domain.error.ApiTokenError
import de.chrgroth.james.platform.domain.model.user.ApiToken
import de.chrgroth.james.platform.domain.model.user.ApiTokenId
import de.chrgroth.james.platform.domain.model.user.ApiTokenScope
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.out.user.ApiTokenRepositoryPort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ApiTokenServiceTests {

  private val apiTokenRepository: ApiTokenRepositoryPort = mockk()
  private val userRepository: UserRepositoryPort = mockk()
  private val service = ApiTokenService(apiTokenRepository, userRepository)

  private val developer = User(
    id = UserId("user-1"),
    username = Username("dev"),
    passwordHash = "hash",
    roles = setOf(UserRole.USER, UserRole.DEVELOPER),
    createdAt = Instant.now(),
  )
  private val plainUser = developer.copy(id = UserId("user-2"), username = Username("plain"), roles = setOf(UserRole.USER))

  private fun token(
    scopes: Set<ApiTokenScope> = setOf(ApiTokenScope.USER_READ),
    active: Boolean = true,
    lastUsedAt: Instant? = null,
    userId: UserId = developer.id,
  ) = ApiToken(
    id = ApiTokenId("token-1"),
    userId = userId,
    name = "cli",
    tokenHash = ApiTokenService.hash("jp_secret"),
    tokenPrefix = "jp_secre",
    scopes = scopes,
    appIds = emptySet(),
    active = active,
    createdAt = Instant.now(),
    lastUsedAt = lastUsedAt,
  )

  // region createToken

  @Test
  fun `createToken stores only the hash and returns the plain token once`() {
    every { userRepository.findByUsername(Username("dev")) } returns developer
    val stored = slot<ApiToken>()
    justRun { apiTokenRepository.insert(capture(stored)) }

    val result = service.createToken("dev", "  my agent  ", setOf(ApiTokenScope.DEVELOPER_READ, ApiTokenScope.USER_WRITE), setOf("app-1", " "))

    val created = result.getOrNull()!!
    assertThat(created.plainToken).startsWith(ApiTokenService.TOKEN_PREFIX)
    assertThat(stored.captured.tokenHash).isEqualTo(ApiTokenService.hash(created.plainToken)).isNotEqualTo(created.plainToken)
    assertThat(stored.captured.tokenPrefix).isEqualTo(created.plainToken.take(8))
    assertThat(stored.captured.name).isEqualTo("my agent")
    assertThat(stored.captured.userId).isEqualTo(developer.id)
    assertThat(stored.captured.active).isTrue()
    assertThat(stored.captured.appIds).containsExactly("app-1")
  }

  @Test
  fun `createToken drops the app restriction for pure developer tokens`() {
    every { userRepository.findByUsername(Username("dev")) } returns developer
    val stored = slot<ApiToken>()
    justRun { apiTokenRepository.insert(capture(stored)) }

    service.createToken("dev", "dev", setOf(ApiTokenScope.DEVELOPER_RELEASE), setOf("app-1"))

    assertThat(stored.captured.appIds).isEmpty()
  }

  @Test
  fun `createToken rejects blank name, empty scopes and scopes the user has no role for`() {
    every { userRepository.findByUsername(Username("plain")) } returns plainUser

    assertThat(service.createToken("plain", " ", setOf(ApiTokenScope.USER_READ), emptySet()).leftOrNull()).isEqualTo(ApiTokenError.NAME_BLANK)
    assertThat(service.createToken("plain", "x", emptySet(), emptySet()).leftOrNull()).isEqualTo(ApiTokenError.NO_SCOPES)
    assertThat(service.createToken("plain", "x", setOf(ApiTokenScope.DEVELOPER_READ), emptySet()).leftOrNull()).isEqualTo(ApiTokenError.SCOPE_NOT_PERMITTED)
    assertThat(service.createToken("plain", "x", setOf(ApiTokenScope.USER_IMPORT), emptySet()).leftOrNull()).isEqualTo(ApiTokenError.SCOPE_NOT_PERMITTED)
    verify(exactly = 0) { apiTokenRepository.insert(any()) }
  }

  @Test
  fun `createToken allows the import scope for data importers and keeps its app restriction`() {
    val importer = plainUser.copy(roles = setOf(UserRole.USER, UserRole.DATA_IMPORT))
    every { userRepository.findByUsername(Username("plain")) } returns importer
    val stored = slot<ApiToken>()
    justRun { apiTokenRepository.insert(capture(stored)) }

    service.createToken("plain", "migration", setOf(ApiTokenScope.USER_IMPORT, ApiTokenScope.USER_INSTALL), setOf("app-1"))

    assertThat(stored.captured.scopes).containsExactlyInAnyOrder(ApiTokenScope.USER_IMPORT, ApiTokenScope.USER_INSTALL)
    assertThat(stored.captured.appIds).containsExactly("app-1")
    assertThat(stored.captured.effectiveScopes(importer))
      .containsExactlyInAnyOrder(ApiTokenScope.USER_IMPORT, ApiTokenScope.USER_INSTALL, ApiTokenScope.USER_READ)
    assertThat(stored.captured.effectiveScopes(importer.copy(roles = setOf(UserRole.USER))))
      .containsExactlyInAnyOrder(ApiTokenScope.USER_INSTALL, ApiTokenScope.USER_READ)
  }

  @Test
  fun `createToken fails for unknown user`() {
    every { userRepository.findByUsername(Username("ghost")) } returns null

    assertThat(service.createToken("ghost", "x", setOf(ApiTokenScope.USER_READ), emptySet()).leftOrNull()).isEqualTo(ApiTokenError.USER_NOT_FOUND)
  }

  // endregion

  // region manage

  @Test
  fun `listTokens returns only the tokens of the user sorted by creation`() {
    val older = token().copy(id = ApiTokenId("a"), createdAt = Instant.now().minusSeconds(60))
    val newer = token().copy(id = ApiTokenId("b"))
    every { userRepository.findByUsername(Username("dev")) } returns developer
    every { apiTokenRepository.findAllByUserId(developer.id) } returns listOf(newer, older)

    assertThat(service.listTokens("dev").getOrNull()!!.map { it.id.value }).containsExactly("a", "b")
  }

  @Test
  fun `setActive and deleteToken are scoped to the owning user`() {
    every { userRepository.findByUsername(Username("dev")) } returns developer
    every { apiTokenRepository.setActive(ApiTokenId("token-1"), developer.id, false) } returns true
    every { apiTokenRepository.setActive(ApiTokenId("foreign"), developer.id, true) } returns false
    every { apiTokenRepository.delete(ApiTokenId("token-1"), developer.id) } returns true
    every { apiTokenRepository.delete(ApiTokenId("foreign"), developer.id) } returns false

    assertThat(service.setActive("dev", ApiTokenId("token-1"), false).isRight()).isTrue()
    assertThat(service.setActive("dev", ApiTokenId("foreign"), true).leftOrNull()).isEqualTo(ApiTokenError.TOKEN_NOT_FOUND)
    assertThat(service.deleteToken("dev", ApiTokenId("token-1")).isRight()).isTrue()
    assertThat(service.deleteToken("dev", ApiTokenId("foreign")).leftOrNull()).isEqualTo(ApiTokenError.TOKEN_NOT_FOUND)
  }

  // endregion

  // region authenticate

  @Test
  fun `authenticate resolves owner and effective scopes including implied ones and records usage`() {
    every { apiTokenRepository.findByHash(ApiTokenService.hash("jp_secret")) } returns token(setOf(ApiTokenScope.USER_WRITE, ApiTokenScope.DEVELOPER_RELEASE))
    every { userRepository.findById(developer.id) } returns developer
    justRun { apiTokenRepository.updateLastUsedAt(ApiTokenId("token-1"), any()) }

    val result = service.authenticate("jp_secret")!!

    assertThat(result.owner).isEqualTo(developer)
    assertThat(result.effectiveScopes).containsExactlyInAnyOrder(
      ApiTokenScope.USER_WRITE,
      ApiTokenScope.USER_READ,
      ApiTokenScope.DEVELOPER_RELEASE,
      ApiTokenScope.DEVELOPER_READ,
    )
    verify { apiTokenRepository.updateLastUsedAt(ApiTokenId("token-1"), any()) }
  }

  @Test
  fun `authenticate does not record usage again within a minute`() {
    every { apiTokenRepository.findByHash(any()) } returns token(lastUsedAt = Instant.now().minus(Duration.ofSeconds(10)))
    every { userRepository.findById(developer.id) } returns developer

    assertThat(service.authenticate("jp_secret")).isNotNull()
    verify(exactly = 0) { apiTokenRepository.updateLastUsedAt(any(), any()) }
  }

  @Test
  fun `authenticate rejects unknown, foreign-prefixed and inactive tokens`() {
    every { apiTokenRepository.findByHash(ApiTokenService.hash("jp_unknown")) } returns null
    every { apiTokenRepository.findByHash(ApiTokenService.hash("jp_secret")) } returns token(active = false)

    assertThat(service.authenticate("jp_unknown")).isNull()
    assertThat(service.authenticate("spctl_secret")).isNull()
    assertThat(service.authenticate("jp_secret")).isNull()
  }

  @Test
  fun `authenticate rejects tokens of deleted or inactive users`() {
    every { apiTokenRepository.findByHash(any()) } returns token()
    every { userRepository.findById(developer.id) } returnsMany listOf(null, developer.copy(active = false))

    assertThat(service.authenticate("jp_secret")).isNull()
    assertThat(service.authenticate("jp_secret")).isNull()
  }

  @Test
  fun `authenticate drops scopes whose role the user lost and rejects tokens without any remaining scope`() {
    every { apiTokenRepository.findByHash(any()) } returnsMany listOf(
      token(setOf(ApiTokenScope.USER_READ, ApiTokenScope.DEVELOPER_WRITE), userId = plainUser.id),
      token(setOf(ApiTokenScope.DEVELOPER_WRITE), userId = plainUser.id),
    )
    every { userRepository.findById(plainUser.id) } returns plainUser
    justRun { apiTokenRepository.updateLastUsedAt(any(), any()) }

    assertThat(service.authenticate("jp_secret")!!.effectiveScopes).containsExactly(ApiTokenScope.USER_READ)
    assertThat(service.authenticate("jp_secret")).isNull()
  }

  // endregion
}
