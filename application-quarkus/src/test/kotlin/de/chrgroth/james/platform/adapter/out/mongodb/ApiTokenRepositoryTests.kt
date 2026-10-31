package de.chrgroth.james.platform.adapter.out.mongodb

import de.chrgroth.james.platform.domain.model.user.ApiToken
import de.chrgroth.james.platform.domain.model.user.ApiTokenId
import de.chrgroth.james.platform.domain.model.user.ApiTokenScope
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.port.out.user.ApiTokenRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@QuarkusTest
class ApiTokenRepositoryTests {

  @Inject
  lateinit var apiTokenRepository: ApiTokenRepositoryPort

  private fun newToken(userId: UserId = UserId(UUID.randomUUID().toString())) = ApiToken(
    id = ApiTokenId(UUID.randomUUID().toString()),
    userId = userId,
    name = "agent",
    tokenHash = UUID.randomUUID().toString(),
    tokenPrefix = "jp_abcde",
    scopes = setOf(ApiTokenScope.USER_READ, ApiTokenScope.DEVELOPER_RELEASE),
    appIds = setOf("app-1"),
    active = true,
    createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
    lastUsedAt = null,
  )

  @Test
  fun `insert stores token and findByHash and findAllByUserId retrieve it`() {
    val token = newToken()
    apiTokenRepository.insert(token)

    assertThat(apiTokenRepository.findByHash(token.tokenHash)).isEqualTo(token)
    assertThat(apiTokenRepository.findAllByUserId(token.userId)).containsExactly(token)
    assertThat(apiTokenRepository.findByHash("unknown-hash")).isNull()
  }

  @Test
  fun `setActive only changes tokens of the owning user`() {
    val token = newToken()
    apiTokenRepository.insert(token)

    assertThat(apiTokenRepository.setActive(token.id, UserId("someone-else"), false)).isFalse()
    assertThat(apiTokenRepository.findByHash(token.tokenHash)!!.active).isTrue()

    assertThat(apiTokenRepository.setActive(token.id, token.userId, false)).isTrue()
    assertThat(apiTokenRepository.findByHash(token.tokenHash)!!.active).isFalse()
  }

  @Test
  fun `updateLastUsedAt records usage`() {
    val token = newToken()
    apiTokenRepository.insert(token)
    val usedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    apiTokenRepository.updateLastUsedAt(token.id, usedAt)

    assertThat(apiTokenRepository.findByHash(token.tokenHash)!!.lastUsedAt).isEqualTo(usedAt)
  }

  @Test
  fun `delete only removes tokens of the owning user and deleteAllByUserId removes all of them`() {
    val userId = UserId(UUID.randomUUID().toString())
    val first = newToken(userId)
    val second = newToken(userId)
    apiTokenRepository.insert(first)
    apiTokenRepository.insert(second)

    assertThat(apiTokenRepository.delete(first.id, UserId("someone-else"))).isFalse()
    assertThat(apiTokenRepository.delete(first.id, userId)).isTrue()
    assertThat(apiTokenRepository.findAllByUserId(userId)).containsExactly(second)

    apiTokenRepository.deleteAllByUserId(userId)
    assertThat(apiTokenRepository.findAllByUserId(userId)).isEmpty()
  }
}
