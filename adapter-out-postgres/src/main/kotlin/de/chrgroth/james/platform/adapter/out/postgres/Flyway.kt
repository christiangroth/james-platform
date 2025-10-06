package de.chrgroth.james.platform.adapter.out.postgres

import io.quarkus.arc.All
import io.quarkus.flyway.FlywayDataSource
import io.quarkus.runtime.StartupEvent
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import mu.KLogging
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location
import org.flywaydb.core.api.configuration.FluentConfiguration
import javax.sql.DataSource

@ApplicationScoped
@Suppress("Unused")
class FlywayProducer {

  @Produces
  @FlywayDataSource("user")
  fun userFlyway(dataSource: DataSource): Flyway {
    return createFlywayConfiguration(dataSource, "user")
  }

  @Produces
  @FlywayDataSource("app")
  fun appFlyway(dataSource: DataSource): Flyway {
    return createFlywayConfiguration(dataSource, "app")
  }

  private fun createFlywayConfiguration(dataSource: DataSource, module: String): Flyway =
    Flyway(
      FluentConfiguration()
        .dataSource(dataSource)
        .defaultSchema("${module}_domain")
        .locations(Location("classpath:db/migration/$module/"))
        .validateOnMigrate(true)
    )
}

@ApplicationScoped
@Suppress("Unused")
class FlywayMigrationExecutor {

  @All
  @Inject
  private lateinit var flywayConfigurations: MutableList<Flyway>

  @Suppress("UnusedParameter")
  fun startup(@Observes @Priority(1) event: StartupEvent) {
    flywayConfigurations.forEach {

      if (it.configuration.defaultSchema.isNullOrBlank()) {
        logger.info { "Skipping default Flyway configuration without default schema ..." }
        return@forEach
      }

      logger.info { "Migrating Flyway configuration for schema ${it.configuration.defaultSchema}..." }
      it.migrate()

      logger.info { "Validating Flyway migration for schema ${it.configuration.defaultSchema}..." }
      it.validate()
    }
  }

  companion object : KLogging()
}
