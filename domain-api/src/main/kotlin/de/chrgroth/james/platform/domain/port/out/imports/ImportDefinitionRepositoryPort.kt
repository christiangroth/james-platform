package de.chrgroth.james.platform.domain.port.out.imports

import de.chrgroth.james.platform.domain.model.imports.ImportDefinition
import de.chrgroth.james.platform.domain.model.imports.ImportDefinitionId

interface ImportDefinitionRepositoryPort {
  fun findAllByUserId(userId: String): List<ImportDefinition>
  fun findById(id: ImportDefinitionId): ImportDefinition?
  fun save(importDefinition: ImportDefinition)
  fun delete(id: ImportDefinitionId)
}
