package de.chrgroth.james.platform.domain.port.`in`.imports

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.imports.DryRunAcceptResult
import de.chrgroth.james.platform.domain.model.imports.DryRunReport
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

  /** Builds all target objects for the mapping (without saving them) and validates each against the target entity definition's constraints. */
  fun dryRun(userId: String, installedAppId: String, importDocumentId: String): Either<DomainError, DryRunReport>

  /** Saves every valid object from the current dry-run, discards invalid ones, and deletes the [ImportDocument] (including its raw payload). */
  fun acceptDryRun(userId: String, installedAppId: String, importDocumentId: String): Either<DomainError, DryRunAcceptResult>
}
