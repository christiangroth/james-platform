package de.chrgroth.spotify.control.adapter.`in`.web

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.TokenError
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TokenEncryptionAdapterTests {

  // 32-byte key encoded as base64 (all zeros for testing)
  private val testKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
  private val adapter = TokenEncryptionAdapter(testKey)

  @Test
  fun `encrypt and decrypt round-trip succeeds`() {
    val plaintext = "hello-world"
    val encryptResult = adapter.encrypt(plaintext)
    assertThat(encryptResult.isRight()).isTrue()
    val decryptResult = adapter.decrypt((encryptResult as Either.Right).value)
    assertThat(decryptResult.isRight()).isTrue()
    assertThat((decryptResult as Either.Right).value).isEqualTo(plaintext)
  }

  @Test
  fun `encrypt produces different ciphertexts for the same input`() {
    val plaintext = "same-input"
    val first = adapter.encrypt(plaintext)
    val second = adapter.encrypt(plaintext)
    assertThat(first).isNotEqualTo(second)
  }

  @Test
  fun `decrypt returns invalid format error for missing dot separator`() {
    val result = adapter.decrypt("not-a-valid-ciphertext")
    assertThat(result.isLeft()).isTrue()
    assertThat((result as Either.Left).value).isEqualTo(TokenError.INVALID_FORMAT)
  }

  @Test
  fun `decrypt returns decryption failed error for tampered ciphertext`() {
    val ciphertext = (adapter.encrypt("sensitive") as Either.Right).value
    val tampered = ciphertext.dropLast(4) + "XXXX"
    val result = adapter.decrypt(tampered)
    assertThat(result.isLeft()).isTrue()
    assertThat((result as Either.Left).value).isEqualTo(TokenError.DECRYPTION_FAILED)
  }
}
