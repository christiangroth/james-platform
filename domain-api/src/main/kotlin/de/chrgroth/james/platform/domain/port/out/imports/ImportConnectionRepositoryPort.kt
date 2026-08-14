package de.chrgroth.james.platform.domain.port.out.imports

import de.chrgroth.james.platform.domain.model.imports.ImportConnection
import de.chrgroth.james.platform.domain.model.imports.ImportConnectionId

interface ImportConnectionRepositoryPort {
  fun findAllByUserId(userId: String): List<ImportConnection>
  fun findById(id: ImportConnectionId): ImportConnection?
  fun save(importConnection: ImportConnection)
  fun delete(id: ImportConnectionId)
}
