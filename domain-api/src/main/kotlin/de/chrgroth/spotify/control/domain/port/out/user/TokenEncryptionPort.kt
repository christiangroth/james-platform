package de.chrgroth.spotify.control.domain.port.out.user

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError

interface TokenEncryptionPort {
  fun encrypt(plaintext: String): Either<DomainError, String>
  fun decrypt(ciphertext: String): Either<DomainError, String>
}
