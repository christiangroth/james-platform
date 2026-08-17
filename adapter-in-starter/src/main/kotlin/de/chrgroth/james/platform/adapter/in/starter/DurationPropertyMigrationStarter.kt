package de.chrgroth.james.platform.adapter.`in`.starter

import de.chrgroth.james.platform.domain.port.`in`.app.AppDataMigrationPort
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class DurationPropertyMigrationStarter(
  private val appDataMigration: AppDataMigrationPort,
) : Starter {

  override val id = "DurationPropertyMigrationStarter-v1"

  override fun execute() {
    appDataMigration.migrateDurationProperties()
  }
}
