package de.chrgroth.spotify.control.domain.user

import de.chrgroth.spotify.control.domain.model.user.User
import de.chrgroth.spotify.control.domain.model.user.UserRole
import de.chrgroth.spotify.control.domain.port.out.infra.NotificationPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.security.SecureRandom
import java.time.Instant

@ApplicationScoped
@Suppress("Unused")
class AdminUserInitializerService(
  private val userRepository: UserRepositoryPort,
  private val notificationPort: NotificationPort,
) {

  fun initializeAdminUser() {
    val existing = userRepository.findByUsername(ADMIN_USERNAME)
    if (existing != null) {
      logger.info { "Admin user already exists, skipping initialization" }
      return
    }

    val password = generateRandomPassword()
    val passwordHash = LoginService.hashPassword(password)
    val admin = User(
      username = ADMIN_USERNAME,
      passwordHash = passwordHash,
      roles = setOf(UserRole.ADMIN),
      createdAt = Instant.now(),
    )
    userRepository.save(admin)

    val message = "Admin user created. Username: $ADMIN_USERNAME, Password: $password"
    logger.warn { message }
    notificationPort.notify(message)
  }

  private fun generateRandomPassword(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    val random = SecureRandom()
    return (1..PASSWORD_LENGTH).map { chars[random.nextInt(chars.length)] }.joinToString("")
  }

  companion object : KLogging() {
    private const val ADMIN_USERNAME = "admin"
    private const val PASSWORD_LENGTH = 16
  }
}
