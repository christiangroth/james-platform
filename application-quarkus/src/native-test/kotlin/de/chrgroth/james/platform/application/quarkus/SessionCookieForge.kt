package de.chrgroth.james.platform.application.quarkus

import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// @TestSecurity (used by the @QuarkusTest/JVM page tests) only fakes a SecurityIdentity in-process, which doesn't
// reach the packaged artifact exercised by @QuarkusIntegrationTest checks - the native executable is a separate OS
// process reached only over HTTP. Instead, a james-session cookie is forged with the fixed `%test.app.token-
// encryption-key` (see domain-impl/src/main/resources/application.properties, active here via
// `quarkus.test.integration-test-profile=test`), the same way CookieAuthMechanism decrypts it: the payload is
// "$username|$issuedAtEpochSeconds" AES/GCM-encrypted, and CookieAuthMechanism looks the username up via
// UserRepositoryPort - so the username passed here must be an actual user that exists in the database (e.g. the
// "admin" user the AdminUserInitializerStarter always creates on startup, in every profile). See #672.
internal object SessionCookieForge {
  const val COOKIE_NAME = "james-session"

  private const val TEST_ENCRYPTION_KEY_BASE64 = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
  private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
  private const val GCM_IV_LENGTH = 12
  private const val GCM_TAG_LENGTH = 128
  private const val PAYLOAD_SEPARATOR = "|"

  fun forgeSessionCookie(username: String): String {
    val payload = "$username$PAYLOAD_SEPARATOR${Instant.now().epochSecond}"
    val secretKey = SecretKeySpec(Base64.getDecoder().decode(TEST_ENCRYPTION_KEY_BASE64), "AES")
    val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
    val ciphertext = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
    val encoder = Base64.getEncoder()
    return "${encoder.encodeToString(iv)}.${encoder.encodeToString(ciphertext)}"
  }
}
