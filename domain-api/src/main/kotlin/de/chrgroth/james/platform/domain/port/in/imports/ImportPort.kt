package de.chrgroth.james.platform.domain.port.`in`.imports

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.imports.DryRunAcceptResult
import de.chrgroth.james.platform.domain.model.imports.DryRunReport
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.FilterRule
import de.chrgroth.james.platform.domain.model.imports.FilterView
import de.chrgroth.james.platform.domain.model.imports.ImportJob
import de.chrgroth.james.platform.domain.model.imports.MappingView

interface ImportPort {
  /** Lists all of [userId]'s import jobs across every installed app, newest first. */
  fun listAllImportJobs(userId: String): List<ImportJob>

  /**
   * Fetches from the connection's base URL - or, when [urlPostfix] is given, from the base URL with it appended -
   * through the connection's stored credentials, and creates a new job targeting [targetEntityDefinitionId]
   * within [installedAppId].
   */
  fun triggerImport(userId: String, installedAppId: String, connectionId: String, targetEntityDefinitionId: String, urlPostfix: String? = null): Either<DomainError, ImportJob>
  fun deleteImportJob(userId: String, importJobId: String): Either<DomainError, Unit>
  fun selectDataPath(userId: String, importJobId: String, dataPath: String): Either<DomainError, ImportJob>
  fun getFilterView(userId: String, importJobId: String): Either<DomainError, FilterView>

  /** Replaces the job's filter rules, applied in order before mapping/dry-run ever see a source record (see [FilterRule]). */
  fun updateFilter(userId: String, importJobId: String, filterRules: List<FilterRule>): Either<DomainError, FilterView>
  fun getMappingView(userId: String, importJobId: String): Either<DomainError, MappingView>
  fun updateMapping(
    userId: String,
    importJobId: String,
    fieldMappings: List<FieldMapping>,
  ): Either<DomainError, MappingView>

  /** Builds all target objects for the mapping (without saving them) and validates each against the target entity definition's constraints. */
  fun dryRun(userId: String, importJobId: String): Either<DomainError, DryRunReport>

  /**
   * Saves every valid object from the current dry-run, discards invalid ones, and deletes the [ImportJob] (including its raw payload).
   * When [replaceExisting] is set, every existing instance of the target entity is deleted first, and the dry-run is re-evaluated
   * against that now-empty state — so a record only skipped because it collided with data that is about to be deleted ends up saved instead.
   */
  fun acceptDryRun(userId: String, importJobId: String, replaceExisting: Boolean): Either<DomainError, DryRunAcceptResult>
}
