package de.chrgroth.james.platform.adapter.`in`.starter

import de.chrgroth.james.platform.domain.port.`in`.imports.ImportConnectionDataMigrationPort
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class ImportConnectionBaseUrlMigrationStarter(
  private val importConnectionDataMigration: ImportConnectionDataMigrationPort,
) : Starter {

  override val id = "ImportConnectionBaseUrlMigrationStarter-v1"

  override fun execute() {
    importConnectionDataMigration.renameUrlFieldToBaseUrl()
  }
}
