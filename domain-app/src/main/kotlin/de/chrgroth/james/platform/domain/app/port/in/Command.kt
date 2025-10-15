package de.chrgroth.james.platform.domain.app.port.`in`

import arrow.core.ValidatedNel
import de.chrgroth.james.DomainError

interface AppCommandPort {
    fun create(): ValidatedNel<DomainError, Unit>
}
