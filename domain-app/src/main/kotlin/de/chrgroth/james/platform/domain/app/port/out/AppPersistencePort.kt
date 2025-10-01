package de.chrgroth.james.platform.domain.app.port.out

import arrow.core.ValidatedNel
import de.chrgroth.james.DomainError
import de.chrgroth.james.platform.domain.app.App
import de.chrgroth.james.platform.domain.app.AppId

interface AppPersistencePort {
    fun byId(id: AppId): ValidatedNel<DomainError, App?>
    fun all(): ValidatedNel<DomainError, Set<App>>
    fun create(app: App): ValidatedNel<DomainError, Unit>
}
