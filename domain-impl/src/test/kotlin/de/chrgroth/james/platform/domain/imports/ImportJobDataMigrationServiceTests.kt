package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.port.out.imports.ImportJobRepositoryPort
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class ImportJobDataMigrationServiceTests {

  private val importJobRepository: ImportJobRepositoryPort = mockk()
  private val service = ImportJobDataMigrationService(importJobRepository)

  @Test
  fun `migrateLongToDurationFieldMappingConversion delegates to repository`() {
    justRun { importJobRepository.migrateLongToDurationFieldMappingConversion() }

    service.migrateLongToDurationFieldMappingConversion()

    verify(exactly = 1) { importJobRepository.migrateLongToDurationFieldMappingConversion() }
  }
}
