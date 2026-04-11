package de.chrgroth.james.platform.domain.user

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.error.LoginError
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.`in`.user.LoginServicePort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class LoginService(
  private val userRepository: UserRepositoryPort,
) : LoginServicePort {

  override fun login(username: String, password: String): Either<DomainError, User> {
    val user = userRepository.findByUsername(Username(username)) ?: run {
      logger.warn { "Login failed: user not found: $username" }
      return LoginError.INVALID_CREDENTIALS.left()
    }
    return if (verifyPassword(password, user.passwordHash)) {
      logger.info { "Login successful for user: $username" }
      user.right()
    } else {
      logger.warn { "Login failed: invalid password for user: $username" }
      LoginError.INVALID_CREDENTIALS.left()
    }
  }

  companion object : KLogging() {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val HASH_PARTS_COUNT = 3

    fun hashPassword(password: String): String {
      val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
      val hash = deriveKey(password, salt)
      val encoder = Base64.getEncoder()
      return "$ITERATIONS:${encoder.encodeToString(salt)}:${encoder.encodeToString(hash)}"
    }

    fun verifyPassword(password: String, storedHash: String): Boolean {
      val parts = storedHash.split(":")
      if (parts.size != HASH_PARTS_COUNT) return false
      return try {
        val iterations = parts[0].toInt()
        val decoder = Base64.getDecoder()
        val salt = decoder.decode(parts[1])
        val expectedHash = decoder.decode(parts[2])
        val actualHash = deriveKey(password, salt, iterations)
        expectedHash.contentEquals(actualHash)
      } catch (e: Exception) {
        logger.warn(e) { "Password verification failed due to unexpected error" }
        false
      }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
      val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
      return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }
  }
}
