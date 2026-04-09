package de.chrgroth.spotify.control.domain.user

import arrow.core.Either
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.user.UserProfilePort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAuthPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class UserProfileService(
  private val userRepository: UserRepositoryPort,
  private val spotifyAccessToken: SpotifyAccessTokenPort,
  private val spotifyAuth: SpotifyAuthPort,
  private val outboxPort: OutboxPort,
) : UserProfilePort {

  override fun getDisplayName(userId: UserId): String? = userRepository.findById(userId)?.displayName

  override fun enqueueUpdates() {
    val users = userRepository.findAll()
    logger.info { "Scheduling profile update for ${users.size} user(s)" }
    users.forEach { user ->
      outboxPort.enqueue(DomainOutboxEvent.UpdateUserProfile(user.spotifyUserId))
    }
  }

  override fun update(userId: UserId): Either<DomainError, Unit> {
    val user = userRepository.findById(userId) ?: run {
      logger.warn { "User not found for profile update: ${userId.value}" }
      return Unit.right()
    }
    val accessToken = spotifyAccessToken.getValidAccessToken(userId)
    return spotifyAuth.getUserProfile(accessToken).map { profile ->
      if (profile.displayName != user.displayName) {
        logger.info { "Updating displayName for user ${userId.value}" }
        userRepository.upsert(user.copy(displayName = profile.displayName))
      }
    }
  }

  override fun handle(event: DomainOutboxEvent.UpdateUserProfile): Either<DomainError, Unit> =
    update(event.userId)

  companion object : KLogging()
}
