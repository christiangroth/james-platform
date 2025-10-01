package de.chrgroth.james.platform.domain.app

import arrow.core.ValidatedNel
import arrow.core.andThen
import arrow.core.invalidNel
import arrow.core.validNel
import arrow.core.zip
import de.chrgroth.james.DomainError
import de.chrgroth.james.platform.domain.app.port.`in`.DomainAppEvents
import de.chrgroth.james.platform.domain.app.port.`in`.EVENT_TOPIC_TO_DOMAIN_APP
import de.chrgroth.james.platform.domain.app.port.`in`.AppCommandPort
import de.chrgroth.james.platform.domain.app.port.out.AppPersistencePort
import io.quarkus.arc.DefaultBean
import io.quarkus.vertx.ConsumeEvent
import io.smallrye.common.annotation.Blocking
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

// TODO run checks in parallel
// TODO validate password policy

@ApplicationScoped
@Suppress("Unused")
internal class AppCommandAdapter : AppCommandPort {

    @Inject
    private lateinit var persistence: AppPersistencePort

    @Blocking
    @Transactional
    @ConsumeEvent(EVENT_TOPIC_TO_DOMAIN_APP)
    fun consume(@Suppress("Unused", "UnusedParameter") event: DomainAppEvents.PersistenceInitialized) {
        logger.info { "AppDatabase created." }
    }

    override fun create(): ValidatedNel<DomainError, Unit> {
        return persistence.create(App())
    }

    companion object : KLogging()
}
