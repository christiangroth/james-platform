package de.chrgroth.james.platform.adapter.`in`.starter

import de.chrgroth.james.platform.domain.port.`in`.imports.ImportJobDataMigrationPort
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class ImportJobLongToDurationConversionMigrationStarter(
  private val importJobDataMigration: ImportJobDataMigrationPort,
) : Starter {

  override val id = "ImportJobLongToDurationConversionMigrationStarter-v1"

  override fun execute() {
    importJobDataMigration.migrateLongToDurationFieldMappingConversion()
  }
}
