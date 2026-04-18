package de.chrgroth.james.platform.domain.user

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.error.UserProfileError
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.`in`.user.UserProfileServicePort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class UserProfileService(
  private val userRepository: UserRepositoryPort,
) : UserProfileServicePort {

  override fun getProfile(username: String): Either<DomainError, User> {
    val user = userRepository.findByUsername(Username(username)) ?: run {
      logger.warn { "Profile lookup failed: user not found: $username" }
      return UserProfileError.USER_NOT_FOUND.left()
    }
    return user.right()
  }

  override fun changeUsername(currentUsername: String, newUsername: String): Either<DomainError, User> {
    val user = userRepository.findByUsername(Username(currentUsername)) ?: run {
      logger.warn { "Change username failed: user not found: $currentUsername" }
      return UserProfileError.USER_NOT_FOUND.left()
    }
    if (userRepository.findByUsername(Username(newUsername)) != null) {
      logger.warn { "Change username failed: new username already exists: $newUsername" }
      return UserProfileError.USERNAME_ALREADY_EXISTS.left()
    }
    val updatedUser = user.copy(username = Username(newUsername))
    userRepository.save(updatedUser)
    logger.info { "Username changed from $currentUsername to $newUsername" }
    return updatedUser.right()
  }

  override fun changePassword(username: String, currentPassword: String, newPassword: String): Either<DomainError, User> {
    val user = userRepository.findByUsername(Username(username)) ?: run {
      logger.warn { "Change password failed: user not found: $username" }
      return UserProfileError.USER_NOT_FOUND.left()
    }
    if (!LoginService.verifyPassword(currentPassword, user.passwordHash)) {
      logger.warn { "Change password failed: invalid current password for user: $username" }
      return UserProfileError.INVALID_CURRENT_PASSWORD.left()
    }
    val updatedUser = user.copy(passwordHash = LoginService.hashPassword(newPassword))
    userRepository.save(updatedUser)
    logger.info { "Password changed for user: $username" }
    return updatedUser.right()
  }

  companion object : KLogging()
}
