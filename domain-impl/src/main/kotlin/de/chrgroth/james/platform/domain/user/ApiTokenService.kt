package de.chrgroth.james.platform.domain.user

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.ApiTokenError
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.user.ApiToken
import de.chrgroth.james.platform.domain.model.user.ApiTokenId
import de.chrgroth.james.platform.domain.model.user.ApiTokenScope
import de.chrgroth.james.platform.domain.model.user.AuthenticatedApiToken
import de.chrgroth.james.platform.domain.model.user.CreatedApiToken
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.`in`.user.ApiTokenPort
import de.chrgroth.james.platform.domain.port.out.user.ApiTokenRepositoryPort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

@ApplicationScoped
@Suppress("Unused")
class ApiTokenService(
  private val apiTokenRepository: ApiTokenRepositoryPort,
  private val userRepository: UserRepositoryPort,
) : ApiTokenPort {

  private val secureRandom = SecureRandom()

  override fun listTokens(username: String): Either<DomainError, List<ApiToken>> {
    val user = findUser(username) ?: return ApiTokenError.USER_NOT_FOUND.left()
    return apiTokenRepository.findAllByUserId(user.id).sortedBy { it.createdAt }.right()
  }

  override fun createToken(username: String, name: String, scopes: Set<ApiTokenScope>, appIds: Set<String>): Either<DomainError, CreatedApiToken> {
    val user = findUser(username) ?: return ApiTokenError.USER_NOT_FOUND.left()
    val trimmedName = name.trim()
    if (trimmedName.isEmpty()) return ApiTokenError.NAME_BLANK.left()
    if (scopes.isEmpty()) return ApiTokenError.NO_SCOPES.left()
    if (scopes.any { it.requiredRole !in user.roles }) return ApiTokenError.SCOPE_NOT_PERMITTED.left()
    val plainToken = generatePlainToken()
    val token = ApiToken(
      id = ApiTokenId(UUID.randomUUID().toString()),
      userId = user.id,
      name = trimmedName,
      tokenHash = hash(plainToken),
      tokenPrefix = plainToken.take(TOKEN_PREFIX_DISPLAY_LENGTH),
      scopes = scopes,
      // the App restriction only applies to User scopes, so it is dropped for pure Developer tokens instead of being shown but having no effect
      appIds = if (scopes.any { it.isUserScope }) appIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet() else emptySet(),
      active = true,
      createdAt = Instant.now(),
      lastUsedAt = null,
    )
    apiTokenRepository.insert(token)
    logger.info { "Created API token '${token.name}' (${token.id.value}) for $username with scopes $scopes, apps ${token.appIds}" }
    return CreatedApiToken(token, plainToken).right()
  }

  override fun setActive(username: String, id: ApiTokenId, active: Boolean): Either<DomainError, Unit> {
    val user = findUser(username) ?: return ApiTokenError.USER_NOT_FOUND.left()
    return if (apiTokenRepository.setActive(id, user.id, active)) {
      logger.info { "${if (active) "Activated" else "Deactivated"} API token ${id.value} of $username" }
      Unit.right()
    } else {
      ApiTokenError.TOKEN_NOT_FOUND.left()
    }
  }

  override fun deleteToken(username: String, id: ApiTokenId): Either<DomainError, Unit> {
    val user = findUser(username) ?: return ApiTokenError.USER_NOT_FOUND.left()
    return if (apiTokenRepository.delete(id, user.id)) {
      logger.info { "Deleted API token ${id.value} of $username" }
      Unit.right()
    } else {
      ApiTokenError.TOKEN_NOT_FOUND.left()
    }
  }

  override fun authenticate(plainToken: String): AuthenticatedApiToken? {
    if (!plainToken.startsWith(TOKEN_PREFIX)) return null
    val token = apiTokenRepository.findByHash(hash(plainToken))?.takeIf { it.active } ?: return null
    val owner = userRepository.findById(token.userId)?.takeIf { it.active } ?: return null
    val effectiveScopes = token.effectiveScopes(owner)
    if (effectiveScopes.isEmpty()) return null
    val now = Instant.now()
    // Usage is recorded with minute granularity so a burst of CLI calls does not turn into one write per request.
    val lastUsedAt = token.lastUsedAt
    if (lastUsedAt == null || Duration.between(lastUsedAt, now) > LAST_USED_UPDATE_INTERVAL) {
      apiTokenRepository.updateLastUsedAt(token.id, now)
    }
    return AuthenticatedApiToken(token, owner, effectiveScopes)
  }

  private fun findUser(username: String): User? = userRepository.findByUsername(Username(username))

  private fun generatePlainToken(): String {
    val bytes = ByteArray(TOKEN_RANDOM_BYTES)
    secureRandom.nextBytes(bytes)
    return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
  }

  companion object : KLogging() {
    const val TOKEN_PREFIX = "jp_"
    private const val TOKEN_RANDOM_BYTES = 32
    private const val TOKEN_PREFIX_DISPLAY_LENGTH = 8
    private val LAST_USED_UPDATE_INTERVAL: Duration = Duration.ofMinutes(1)

    internal fun hash(plainToken: String): String =
      MessageDigest.getInstance("SHA-256").digest(plainToken.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
  }
}
