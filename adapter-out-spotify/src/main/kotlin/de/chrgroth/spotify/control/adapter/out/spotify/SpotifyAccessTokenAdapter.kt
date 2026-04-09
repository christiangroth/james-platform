package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.raise.either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.RefreshToken
import de.chrgroth.spotify.control.domain.model.user.User
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAuthPort
import de.chrgroth.spotify.control.domain.port.out.user.TokenEncryptionPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class SpotifyAccessTokenAdapter(
  private val spotifyAuth: SpotifyAuthPort,
  private val userRepository: UserRepositoryPort,
  private val tokenEncryption: TokenEncryptionPort,
) : SpotifyAccessTokenPort {

  override fun getValidAccessToken(userId: UserId): AccessToken {
    val user = requireNotNull(userRepository.findById(userId)) { "User not found: ${userId.value}" }
    return if (isTokenExpiringSoon(user)) {
      logger.info { "Token expiring soon, refreshing for user: ${userId.value}" }
      refreshAndPersist(user).fold(
        ifLeft = { error("Failed to refresh access token for user ${userId.value}: ${it.code}") },
        ifRight = { it }
      )
    } else {
      tokenEncryption.decrypt(user.encryptedAccessToken).fold(
        ifLeft = { error("Failed to decrypt access token for user ${userId.value}: ${it.code}") },
        ifRight = { AccessToken(it) }
      )
    }
  }

  private fun isTokenExpiringSoon(user: User): Boolean =
    user.tokenExpiresAt <= Clock.System.now() + TOKEN_REFRESH_BUFFER

  private fun refreshAndPersist(user: User): Either<DomainError, AccessToken> = either {
    val plainRefresh = tokenEncryption.decrypt(user.encryptedRefreshToken).bind()
    val refreshToken = RefreshToken(plainRefresh)
    val refreshed = spotifyAuth.refreshToken(refreshToken).bind()
    val now = Clock.System.now()
    val encryptedAccess = tokenEncryption.encrypt(refreshed.accessToken.value).bind()
    val encryptedRefresh = refreshed.refreshToken
      ?.let { tokenEncryption.encrypt(it.value).bind() }
      ?: user.encryptedRefreshToken
    userRepository.upsert(
      user.copy(
        encryptedAccessToken = encryptedAccess,
        encryptedRefreshToken = encryptedRefresh,
        tokenExpiresAt = now + refreshed.expiresInSeconds.seconds,
      )
    )
    refreshed.accessToken
  }

  companion object : KLogging() {
    private val TOKEN_REFRESH_BUFFER = 5.minutes
  }
}
