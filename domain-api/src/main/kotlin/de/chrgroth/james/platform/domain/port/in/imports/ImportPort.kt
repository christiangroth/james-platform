package de.chrgroth.james.platform.domain.port.`in`.imports

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.imports.DryRunReport
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.FilterRule
import de.chrgroth.james.platform.domain.model.imports.FilterSample
import de.chrgroth.james.platform.domain.model.imports.FilterView
import de.chrgroth.james.platform.domain.model.imports.ImportJob
import de.chrgroth.james.platform.domain.model.imports.MappingSample
import de.chrgroth.james.platform.domain.model.imports.MappingView
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent

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

  /** Replaces the job's definition's filter rules, applied in order before mapping/dry-run ever see a source record (see [FilterRule]). */
  fun updateFilter(userId: String, importJobId: String, filterRules: List<FilterRule>): Either<DomainError, FilterView>

  /** Distinct textual values found in the job's source records at [sourcePath], for the filter UI to offer as picks instead of free text (see [de.chrgroth.james.platform.domain.imports.FilterEvaluator.distinctValues]). */
  fun resolveFilterFieldValues(userId: String, importJobId: String, sourcePath: String): Either<DomainError, List<String>>

  /**
   * A single source record at [index] within the matched (or, with [matched] false, excluded) side of the job's
   * filter pipeline, for the filter step's live preview (see [FilterSample]).
   */
  fun resolveFilterSample(userId: String, importJobId: String, matched: Boolean, index: Int): Either<DomainError, FilterSample>
  fun getMappingView(userId: String, importJobId: String): Either<DomainError, MappingView>
  fun updateMapping(
    userId: String,
    importJobId: String,
    fieldMappings: List<FieldMapping>,
  ): Either<DomainError, MappingView>

  /** Builds all target objects for the mapping (without saving them) and validates each against the target entity definition's constraints. */
  fun dryRun(userId: String, importJobId: String): Either<DomainError, DryRunReport>

  /**
   * A single source record at [index] within the job's filtered record set, together with the [DryRunObject] the
   * given, not yet saved, [fieldMappings] would produce for it - the Mapping step's live preview (see
   * [MappingSample]). Runs the dry-run logic against a batch of one record only, so recalculating the preview after
   * a mapping rule change stays cheap regardless of import job size.
   */
  fun resolveMappingSample(userId: String, importJobId: String, index: Int, fieldMappings: List<FieldMapping>): Either<DomainError, MappingSample>

  /**
   * Marks the job as [de.chrgroth.james.platform.domain.model.imports.ImportStatus.ACCEPTING] and enqueues a
   * [DomainOutboxEvent.AcceptDryRun] outbox event for background processing (see [handle]), instead of running
   * the accept synchronously in the request - the source data volume is unbounded, so it could otherwise exceed
   * the request timeout. Returns the updated job immediately; the caller must poll or reload to observe completion
   * (the job is deleted by [handle] once processing finishes).
   */
  fun acceptDryRun(userId: String, importJobId: String, replaceExisting: Boolean): Either<DomainError, ImportJob>

  /**
   * Performs the actual accept: saves every valid object from the current dry-run, discards invalid ones, and
   * deletes the [ImportJob] (including its raw payload) - its [de.chrgroth.james.platform.domain.model.imports.ImportDefinition]
   * is left untouched so it can be reused for a later import. When [DomainOutboxEvent.AcceptDryRun.replaceExisting]
   * is set, every existing instance of the target entity is deleted first, and the dry-run is re-evaluated against
   * that now-empty state — so a record only skipped because it collided with data that is about to be deleted ends
   * up saved instead. Called by the outbox dispatcher, not directly by inbound adapters. A no-longer-existing
   * import job (already processed by a prior delivery, or deleted by the user) is treated as a no-op success.
   */
  fun handle(event: DomainOutboxEvent.AcceptDryRun): Either<DomainError, Unit>
}
