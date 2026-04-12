package de.chrgroth.james.platform.domain.user

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.error.UserAdminError
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.`in`.user.AdminUserManagementPort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.Instant

@ApplicationScoped
@Suppress("Unused")
class AdminUserManagementService(
  private val userRepository: UserRepositoryPort,
) : AdminUserManagementPort {

  override fun listUsers(): List<User> = userRepository.findAll()

  override fun createUser(username: String, password: String, callingUsername: String): Either<DomainError, User> {
    if (username.isBlank() || password.isBlank()) {
      logger.warn { "Create user failed: blank input from $callingUsername" }
      return UserAdminError.BLANK_INPUT.left()
    }
    if (userRepository.findByUsername(Username(username)) != null) {
      logger.warn { "Create user failed: username already exists: $username" }
      return UserAdminError.USERNAME_ALREADY_EXISTS.left()
    }
    val user = User(
      username = Username(username),
      passwordHash = LoginService.hashPassword(password),
      roles = setOf(UserRole.USER),
      createdAt = Instant.now(),
      active = true,
    )
    userRepository.save(user)
    logger.info { "User created: $username by $callingUsername" }
    return user.right()
  }

  override fun activateUser(username: String): Either<DomainError, User> {
    val user = userRepository.findByUsername(Username(username)) ?: run {
      logger.warn { "Activate user failed: user not found: $username" }
      return UserAdminError.USER_NOT_FOUND.left()
    }
    val updatedUser = user.copy(active = true)
    userRepository.save(updatedUser)
    logger.info { "User activated: $username" }
    return updatedUser.right()
  }

  override fun deactivateUser(username: String, callingUsername: String): Either<DomainError, User> {
    if (username == callingUsername) {
      logger.warn { "Deactivate user failed: cannot deactivate self: $username" }
      return UserAdminError.CANNOT_DEACTIVATE_SELF.left()
    }
    val user = userRepository.findByUsername(Username(username)) ?: run {
      logger.warn { "Deactivate user failed: user not found: $username" }
      return UserAdminError.USER_NOT_FOUND.left()
    }
    val updatedUser = user.copy(active = false)
    userRepository.save(updatedUser)
    logger.info { "User deactivated: $username by $callingUsername" }
    return updatedUser.right()
  }

  override fun setPassword(username: String, newPassword: String): Either<DomainError, User> {
    if (newPassword.isBlank()) {
      logger.warn { "Set password failed: blank input for $username" }
      return UserAdminError.BLANK_INPUT.left()
    }
    val user = userRepository.findByUsername(Username(username)) ?: run {
      logger.warn { "Set password failed: user not found: $username" }
      return UserAdminError.USER_NOT_FOUND.left()
    }
    val updatedUser = user.copy(passwordHash = LoginService.hashPassword(newPassword))
    userRepository.save(updatedUser)
    logger.info { "Password set for user: $username" }
    return updatedUser.right()
  }

  override fun deleteUser(username: String, callingUsername: String): Either<DomainError, Unit> {
    if (username == callingUsername) {
      logger.warn { "Delete user failed: cannot delete self: $username" }
      return UserAdminError.CANNOT_DELETE_SELF.left()
    }
    userRepository.findByUsername(Username(username)) ?: run {
      logger.warn { "Delete user failed: user not found: $username" }
      return UserAdminError.USER_NOT_FOUND.left()
    }
    userRepository.delete(Username(username))
    logger.info { "User deleted: $username by $callingUsername" }
    return Unit.right()
  }

  companion object : KLogging()
}
