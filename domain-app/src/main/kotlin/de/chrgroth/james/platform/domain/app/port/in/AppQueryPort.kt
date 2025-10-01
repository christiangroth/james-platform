package de.chrgroth.james.platform.domain.app.port.`in`

import arrow.core.ValidatedNel
import de.chrgroth.james.DomainError
import de.chrgroth.james.platform.domain.app.App
import de.chrgroth.james.platform.domain.app.AppId

interface AppQueryPort {
    fun all(): ValidatedNel<DomainError, Set<App>>
    fun byId(id: AppId): ValidatedNel<DomainError, App?>
}
