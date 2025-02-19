package de.chrgroth.james.platform.domain.user

import arrow.core.ValidatedNel
import arrow.core.validNel
import de.chrgroth.james.DomainError

// TODO create password hash
internal fun String.hashPassword(): ValidatedNel<DomainError, String> {
  return this.validNel()
}
