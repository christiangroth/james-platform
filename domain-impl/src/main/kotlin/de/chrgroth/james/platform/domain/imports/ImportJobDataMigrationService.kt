package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.port.`in`.imports.ImportJobDataMigrationPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportJobRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class ImportJobDataMigrationService(
  private val importJobRepository: ImportJobRepositoryPort,
) : ImportJobDataMigrationPort {

  override fun migrateLongToDurationFieldMappingConversion() {
    logger.info { "Migrating LONG_TO_DURATION field mapping conversions" }
    importJobRepository.migrateLongToDurationFieldMappingConversion()
    logger.info { "Migration of LONG_TO_DURATION field mapping conversions completed" }
  }

  companion object : KLogging()
}
