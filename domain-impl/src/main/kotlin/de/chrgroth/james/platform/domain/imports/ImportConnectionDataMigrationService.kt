package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.port.`in`.imports.ImportConnectionDataMigrationPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportConnectionRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class ImportConnectionDataMigrationService(
  private val importConnectionRepository: ImportConnectionRepositoryPort,
) : ImportConnectionDataMigrationPort {

  override fun renameUrlFieldToBaseUrl() {
    logger.info { "Renaming import connection url field to baseUrl" }
    importConnectionRepository.renameUrlFieldToBaseUrl()
    logger.info { "Renaming of import connection url field to baseUrl completed" }
  }

  companion object : KLogging()
}
