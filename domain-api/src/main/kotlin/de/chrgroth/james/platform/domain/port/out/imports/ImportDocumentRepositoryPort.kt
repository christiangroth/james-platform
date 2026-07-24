package de.chrgroth.james.platform.domain.port.out.imports

import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.imports.ImportDocument
import de.chrgroth.james.platform.domain.model.imports.ImportDocumentId

interface ImportDocumentRepositoryPort {
  fun findAllByInstalledAppId(installedAppId: InstalledAppId): List<ImportDocument>
  fun findById(id: ImportDocumentId): ImportDocument?
  fun save(importDocument: ImportDocument)
  fun delete(id: ImportDocumentId)
}
