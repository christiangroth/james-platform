package de.chrgroth.james.platform.domain.port.out.user

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError

interface TokenEncryptionPort {
  fun encrypt(plaintext: String): Either<DomainError, String>
  fun decrypt(ciphertext: String): Either<DomainError, String>
}
