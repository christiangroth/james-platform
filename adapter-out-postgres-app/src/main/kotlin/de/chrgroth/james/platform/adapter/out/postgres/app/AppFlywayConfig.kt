package de.chrgroth.james.platform.adapter.out.postgres.app

import de.chrgroth.james.platform.domain.app.port.`in`.DomainAppEvents
import de.chrgroth.james.platform.domain.app.port.`in`.EVENT_TOPIC_TO_DOMAIN_APP
import io.agroal.api.AgroalDataSource
import io.quarkus.flyway.FlywayConfigurationCustomizer
import io.quarkus.runtime.StartupEvent
import io.vertx.core.eventbus.EventBus
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import jakarta.resource.spi.ConfigProperty
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import mu.KLogging

@ApplicationScoped
class AppFlywayConfig {

    @Inject
    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    lateinit var datasourceUrl: String

    @Inject
    @ConfigProperty(name = "quarkus.datasource.username")
    lateinit var username: String

    @Inject
    @ConfigProperty(name = "quarkus.datasource.password")
    lateinit var password: String

    @Inject
    private lateinit var eventBus: EventBus

    @Suppress("Unused")
    fun startup(@Observes @Priority(1) @Suppress("UnusedParameter") event: StartupEvent) {
        logger.info { "Loading Flyway..." }
        val flyway = Flyway.configure()
            .dataSource(datasourceUrl, username, password)
            .locations("classpath:db/migration/app")
            .schemas("app_domain")
            .load()

        logger.info { "Migrating databse..." }
        flyway.migrate()

        logger.info { "Databse migration done." }
        eventBus.publish(EVENT_TOPIC_TO_DOMAIN_APP, DomainAppEvents.PersistenceInitialized)
    }

    companion object : KLogging()
}
