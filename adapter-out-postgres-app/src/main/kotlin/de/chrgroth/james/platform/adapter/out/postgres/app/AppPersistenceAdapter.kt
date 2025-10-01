package de.chrgroth.james.platform.adapter.out.postgres.app

import arrow.core.ValidatedNel
import arrow.core.invalidNel
import arrow.core.valid
import de.chrgroth.james.DomainError
import de.chrgroth.james.platform.domain.app.App
import de.chrgroth.james.platform.domain.app.AppDomainErrorCodes
import de.chrgroth.james.platform.domain.app.AppId
import de.chrgroth.james.platform.domain.app.port.`in`.DomainAppEvents
import de.chrgroth.james.platform.domain.app.port.`in`.EVENT_TOPIC_TO_DOMAIN_APP
import de.chrgroth.james.platform.domain.app.port.out.AppPersistencePort
import io.quarkus.runtime.StartupEvent
import io.vertx.core.eventbus.EventBus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityExistsException
import jakarta.persistence.EntityManager
import jakarta.persistence.Id
import jakarta.persistence.PersistenceUnit
import jakarta.persistence.Table
import mu.KLogging

// TODO check caching
// TODO check Hibernate Envers
// TODO https://quarkus.io/guides/hibernate-orm#multitenancy check for user data entities later on
// TODO https://quarkus.io/guides/hibernate-orm#json_xml_serialization_deserialization might be useful for user type definitions and apps

// TODO Hibernate sucks for entity definitions
@Entity
@Table(name = "apps", schema = "app_domain")
open class AppEntity(

    @Id
    @Column(unique = true, nullable = false, name = "id")
    private val id: String,
) {
    constructor() : this(
        id = "",
    )

    constructor(app: App) : this(
        app.id.value,
    )

    fun toDomain(): App =
        App.fromEntity(
            id = AppId(id),
        )
}

@ApplicationScoped
@Suppress("Unused")
class AppPersistenceAdapter : AppPersistencePort {

    @Inject
    private lateinit var eventBus: EventBus

    @Inject
    @PersistenceUnit(unitName = "app")
    lateinit var entityManager: EntityManager

    @Suppress("Unused")
    fun startup(@Observes @Suppress("UnusedParameter") event: StartupEvent) {
        logger.info { "AppDatabase created." }
        eventBus.publish(EVENT_TOPIC_TO_DOMAIN_APP, DomainAppEvents.PersistenceInitialized)
    }

    override fun byId(id: AppId): ValidatedNel<DomainError, App?> = runCatching {
        entityManager.find(AppEntity::class.java, id.value)?.toDomain()
    }.fold(
        onSuccess = { it.valid() },
        onFailure = {
            DomainError(
                code = AppDomainErrorCodes.APP_QUERY_FAILED,
                errorMessage = it.message
            ).invalidNel()
        }
    )

    override fun all(): ValidatedNel<DomainError, Set<App>> = runCatching {
        entityManager.createQuery("SELECT a FROM AppEntity a", AppEntity::class.java)
            .resultList
            .map { it.toDomain() }
            .toSet()
    }.fold(
        onSuccess = { it.valid() },
        onFailure = { DomainError(code = AppDomainErrorCodes.APP_QUERY_FAILED, errorMessage = it.message).invalidNel() }
    )

    override fun create(app: App): ValidatedNel<DomainError, Unit> = runCatching {
        entityManager.persist(AppEntity(app))
    }.fold(
        onSuccess = { Unit.valid() },
        onFailure = {
            if (it is EntityExistsException) {
                DomainError(
                    code = AppDomainErrorCodes.APP_EXISTS,
                    errorMessage = null
                )
            } else {
                DomainError(code = AppDomainErrorCodes.APP_QUERY_FAILED, errorMessage = it.message)
            }.invalidNel()
        }
    )

    companion object : KLogging()
}
