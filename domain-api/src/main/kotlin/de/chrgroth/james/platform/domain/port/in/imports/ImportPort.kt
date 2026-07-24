package de.chrgroth.james.platform.domain.port.`in`.imports

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.imports.ImportDocument

interface ImportPort {
  fun listImportDocuments(userId: String, installedAppId: String): Either<DomainError, List<ImportDocument>>
  fun triggerImport(userId: String, installedAppId: String, sourceUrl: String, bearerToken: String): Either<DomainError, ImportDocument>
  fun deleteImportDocument(userId: String, installedAppId: String, importDocumentId: String): Either<DomainError, Unit>
}
