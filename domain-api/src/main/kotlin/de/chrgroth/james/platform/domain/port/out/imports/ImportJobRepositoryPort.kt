package de.chrgroth.james.platform.domain.port.out.imports

import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.imports.ImportJob
import de.chrgroth.james.platform.domain.model.imports.ImportJobId
import java.time.Instant

interface ImportJobRepositoryPort {
  fun findAllByInstalledAppId(installedAppId: InstalledAppId): List<ImportJob>
  fun findById(id: ImportJobId): ImportJob?
  fun save(importJob: ImportJob)
  fun delete(id: ImportJobId)
  fun deleteAllLastChangedBefore(cutoff: Instant): Long
}
