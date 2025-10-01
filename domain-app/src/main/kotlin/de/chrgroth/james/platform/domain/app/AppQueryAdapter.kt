package de.chrgroth.james.platform.domain.app

import arrow.core.ValidatedNel
import arrow.core.andThen
import arrow.core.invalidNel
import arrow.core.validNel
import arrow.core.zip
import de.chrgroth.james.DomainError
import de.chrgroth.james.platform.domain.app.port.`in`.AppQueryPort
import de.chrgroth.james.platform.domain.app.port.out.AppPersistencePort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
@Suppress("Unused")
internal class AppQueryAdapter : AppQueryPort {

    @Inject
    private lateinit var persistence: AppPersistencePort

    override fun all(): ValidatedNel<DomainError, Set<App>> {
        return persistence.all()
    }

    override fun byId(id: AppId): ValidatedNel<DomainError, App?> {
        return persistence.byId(id)
    }
}
