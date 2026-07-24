package de.chrgroth.james.platform.domain.port.`in`.imports

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.ImportDocument
import de.chrgroth.james.platform.domain.model.imports.MappingType
import de.chrgroth.james.platform.domain.model.imports.MappingView

interface ImportPort {
  fun listImportDocuments(userId: String, installedAppId: String): Either<DomainError, List<ImportDocument>>
  fun triggerImport(userId: String, installedAppId: String, sourceUrl: String, bearerToken: String): Either<DomainError, ImportDocument>
  fun deleteImportDocument(userId: String, installedAppId: String, importDocumentId: String): Either<DomainError, Unit>
  fun selectDataPath(userId: String, installedAppId: String, importDocumentId: String, dataPath: String): Either<DomainError, ImportDocument>
  fun getMappingView(userId: String, installedAppId: String, importDocumentId: String): Either<DomainError, MappingView>
  fun updateMapping(
    userId: String,
    installedAppId: String,
    importDocumentId: String,
    name: String,
    type: MappingType,
    targetEntityDefinitionId: String,
    fieldMappings: List<FieldMapping>,
  ): Either<DomainError, MappingView>
}
