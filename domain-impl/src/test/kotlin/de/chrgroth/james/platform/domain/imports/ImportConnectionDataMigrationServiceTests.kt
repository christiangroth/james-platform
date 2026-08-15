package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.port.out.imports.ImportConnectionRepositoryPort
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class ImportConnectionDataMigrationServiceTests {

  private val importConnectionRepository: ImportConnectionRepositoryPort = mockk()
  private val service = ImportConnectionDataMigrationService(importConnectionRepository)

  @Test
  fun `renameUrlFieldToBaseUrl delegates to repository`() {
    justRun { importConnectionRepository.renameUrlFieldToBaseUrl() }

    service.renameUrlFieldToBaseUrl()

    verify(exactly = 1) { importConnectionRepository.renameUrlFieldToBaseUrl() }
  }
}
