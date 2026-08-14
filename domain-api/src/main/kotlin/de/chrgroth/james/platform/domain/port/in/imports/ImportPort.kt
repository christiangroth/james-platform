package de.chrgroth.james.platform.domain.port.`in`.imports

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.imports.DryRunAcceptResult
import de.chrgroth.james.platform.domain.model.imports.DryRunReport
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.ImportJob
import de.chrgroth.james.platform.domain.model.imports.MappingView

interface ImportPort {
  fun listImportJobs(userId: String, installedAppId: String): Either<DomainError, List<ImportJob>>

  /** Fetches the connection's URL through the connection's stored credentials and creates a new job targeting [targetEntityDefinitionId] within [installedAppId]. */
  fun triggerImport(userId: String, installedAppId: String, connectionId: String, targetEntityDefinitionId: String): Either<DomainError, ImportJob>
  fun deleteImportJob(userId: String, importJobId: String): Either<DomainError, Unit>
  fun selectDataPath(userId: String, importJobId: String, dataPath: String): Either<DomainError, ImportJob>
  fun getMappingView(userId: String, importJobId: String): Either<DomainError, MappingView>
  fun updateMapping(
    userId: String,
    importJobId: String,
    fieldMappings: List<FieldMapping>,
  ): Either<DomainError, MappingView>

  /** Builds all target objects for the mapping (without saving them) and validates each against the target entity definition's constraints. */
  fun dryRun(userId: String, importJobId: String): Either<DomainError, DryRunReport>

  /** Saves every valid object from the current dry-run, discards invalid ones, and deletes the [ImportJob] (including its raw payload). */
  fun acceptDryRun(userId: String, importJobId: String): Either<DomainError, DryRunAcceptResult>
}
