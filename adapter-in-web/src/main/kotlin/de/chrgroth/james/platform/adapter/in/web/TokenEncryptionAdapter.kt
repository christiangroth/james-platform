package de.chrgroth.james.platform.adapter.`in`.web

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.error.TokenError
import de.chrgroth.james.platform.domain.port.out.user.EncryptionKeyRepositoryPort
import de.chrgroth.james.platform.domain.port.out.user.TokenEncryptionPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class TokenEncryptionAdapter(
  private val encryptionKeyRepository: EncryptionKeyRepositoryPort,
) : TokenEncryptionPort {

  private val secretKey: SecretKeySpec
  private val random = SecureRandom()

  init {
    val keyBase64 = encryptionKeyRepository.findKey() ?: generateAndStoreKey()
    secretKey = SecretKeySpec(Base64.getDecoder().decode(keyBase64), "AES")
  }

  private fun generateAndStoreKey(): String {
    val keyBytes = ByteArray(KEY_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
    val keyBase64 = Base64.getEncoder().encodeToString(keyBytes)
    encryptionKeyRepository.saveKey(keyBase64)
    logger.info { "Generated and stored new token encryption key" }
    return keyBase64
  }

  override fun encrypt(plaintext: String): Either<DomainError, String> = try {
    val iv = ByteArray(GCM_IV_LENGTH).also { random.nextBytes(it) }
    val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
    val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
    val encoder = Base64.getEncoder()
    "${encoder.encodeToString(iv)}.${encoder.encodeToString(ciphertext)}".right()
  } catch (e: Exception) {
    logger.error(e) { "Encryption failed" }
    TokenError.ENCRYPTION_FAILED.left()
  }

  override fun decrypt(ciphertext: String): Either<DomainError, String> {
    val parts = ciphertext.split(".")
    if (parts.size != 2) return TokenError.INVALID_FORMAT.left()
    return try {
      val decoder = Base64.getDecoder()
      val iv = decoder.decode(parts[0])
      val encrypted = decoder.decode(parts[1])
      val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
      cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
      String(cipher.doFinal(encrypted), Charsets.UTF_8).right()
    } catch (e: Exception) {
      logger.error(e) { "Decryption failed" }
      TokenError.DECRYPTION_FAILED.left()
    }
  }

  companion object : KLogging() {
    private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val KEY_SIZE_BYTES = 32
  }
}
