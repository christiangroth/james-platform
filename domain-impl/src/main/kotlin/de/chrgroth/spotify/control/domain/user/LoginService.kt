package de.chrgroth.spotify.control.domain.user

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import de.chrgroth.spotify.control.domain.error.AuthError
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.user.User
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.`in`.user.LoginServicePort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAuthPort
import de.chrgroth.spotify.control.domain.port.out.user.TokenEncryptionPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class LoginService(
  private val spotifyAuth: SpotifyAuthPort,
  private val userRepository: UserRepositoryPort,
  private val tokenEncryption: TokenEncryptionPort,
  @ConfigProperty(name = "app.allowed-spotify-user-ids")
  allowedUserIdStrings: List<String>,
) : LoginServicePort {

  private val allowedUserIds: Set<UserId> = allowedUserIdStrings.map { UserId(it) }.toSet()

  override fun isAllowed(userId: UserId): Boolean =
    userId in allowedUserIds

  override fun handleCallback(code: String): Either<DomainError, UserId> = try {
    either {
      val tokens = spotifyAuth.exchangeCode(code).bind()
      val profile = spotifyAuth.getUserProfile(tokens.accessToken).bind()
      val userId = UserId(profile.id.value)

      if (!isAllowed(userId)) {
        logger.warn { "Login denied for user: ${userId.value}" }
        raise(AuthError.USER_NOT_ALLOWED)
      }

      val encryptedAccess = tokenEncryption.encrypt(tokens.accessToken.value).bind()
      val encryptedRefresh = tokenEncryption.encrypt(tokens.refreshToken.value).bind()
      val now = Clock.System.now()
      userRepository.upsert(
        User(
          spotifyUserId = userId,
          displayName = profile.displayName,
          encryptedAccessToken = encryptedAccess,
          encryptedRefreshToken = encryptedRefresh,
          tokenExpiresAt = now + tokens.expiresInSeconds.seconds,
          lastLoginAt = now,
        )
      )

      userId
    }
  } catch (e: Exception) {
    logger.error(e) { "Unexpected error during login callback" }
    AuthError.UNEXPECTED.left()
  }

  companion object : KLogging()
}
