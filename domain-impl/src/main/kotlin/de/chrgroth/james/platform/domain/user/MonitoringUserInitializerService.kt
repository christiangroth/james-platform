package de.chrgroth.james.platform.domain.user

import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.`in`.user.MonitoringUserInitializerPort
import de.chrgroth.james.platform.domain.port.out.infra.NotificationPort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

@ApplicationScoped
@Suppress("Unused")
class MonitoringUserInitializerService(
  private val userRepository: UserRepositoryPort,
  private val notificationPort: NotificationPort,
) : MonitoringUserInitializerPort {

  override fun initializeMonitoringUser() {
    val existing = userRepository.findByUsername(Username(MONITORING_USERNAME))
    if (existing != null) {
      if (existing.roles.contains(UserRole.MONITORING)) {
        logger.info { "Monitoring user already has monitoring role, skipping initialization" }
        return
      }
      val updatedUser = existing.copy(roles = existing.roles + UserRole.MONITORING)
      userRepository.save(updatedUser)
      logger.info { "Monitoring role added to existing user: $MONITORING_USERNAME" }
      return
    }

    val password = generateRandomPassword()
    val passwordHash = LoginService.hashPassword(password)
    val monitoringUser = User(
      id = UserId(UUID.randomUUID().toString()),
      username = Username(MONITORING_USERNAME),
      passwordHash = passwordHash,
      roles = setOf(UserRole.USER, UserRole.MONITORING),
      createdAt = Instant.now(),
    )
    userRepository.save(monitoringUser)

    val message = "Monitoring user created. Username: $MONITORING_USERNAME, Password: $password"
    logger.warn { message }
    notificationPort.notify(message)
  }

  private fun generateRandomPassword(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    val random = SecureRandom()
    return (1..PASSWORD_LENGTH).map { chars[random.nextInt(chars.length)] }.joinToString("")
  }

  companion object : KLogging() {
    private const val MONITORING_USERNAME = "chris"
    private const val PASSWORD_LENGTH = 16
  }
}
