package de.chrgroth.james.platform.domain.model.imports

import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import java.time.Instant

@JvmInline
value class ImportJobId(val value: String)

enum class ImportStatus {
  DOWNLOADED,
  DATA_IDENTIFIED,
  READY,

  /** Accept was triggered and enqueued via the outbox; the job is kept around until the background dispatcher finishes processing it. */
  ACCEPTING,

  /**
   * Terminal state: the background dispatcher (`ImportService.handle`) finished accepting the dry run. The job is no
   * longer deleted on accept (see docs/adr/0022-import-job-history.md) so it can be listed as a past run in the
   * "Historie" view for its [ImportDefinition] - see `ImportPort.listAllImportJobs`. Retention is still governed by
   * `ImportCleanupService`, which deletes jobs purely by [ImportJob.lastChangedAt] age regardless of status.
   */
  ACCEPTED,
}

enum class ImportTrigger {
  /** Started interactively by a user through `ImportPort.triggerImport`. */
  USER,

  /** Started unattended by a dispatched `DomainOutboxEvent.RunScheduledImport` through `ImportService.triggerScheduledImport`, reusing an existing [ImportDefinition]'s stored configuration. */
  SYSTEM,
}

data class DataPath(
  val path: String,
  val size: Int,
)

enum class SchemaPropertyType {
  STRING,
  DATE,
  DATETIME,
  LONG,
  DOUBLE,
  BOOLEAN,
  OBJECT,
  ARRAY,
  NULL,
}

data class NumericRange(
  val min: Double,
  val max: Double,
)

data class SchemaProperty(
  val path: String,
  val typeCounts: Map<SchemaPropertyType, Int>,
  val mandatory: Boolean,
  val numericRange: NumericRange? = null,
  val stringLengthCounts: Map<Int, Int> = emptyMap(),
)

/**
 * A single fetch-to-mapping-to-import run targeting a fixed [installedAppId], built from a reusable
 * [ImportDefinition] (referenced by [importDefinitionId]) that carries the connection, target entity, data path,
 * filter rules and mapping. Unlike the definition, a job only holds the data snapshot for one point in time and is
 * cleaned up automatically when it stays inactive too long; accepting it (see `ImportService.handle`) moves it to
 * [ImportStatus.ACCEPTED] instead of deleting it, so it remains visible as a past run in its definition's "Historie"
 * view, while its definition stays in place for reuse - see docs/adr/0021-import-definition-job-split.md and
 * docs/adr/0022-import-job-history.md.
 *
 * [detectedSchema] is derived once from the raw, unfiltered records at the definition's selected data path and stays
 * unchanged afterwards - it is the field reference panel shown across the Filter and Mapping steps. [filteredSchema]
 * is recomputed and persisted whenever the definition's filter rules change (see `ImportService.updateFilter`),
 * since filter rules can change which records survive and thus a property's mandatory-ness, value range, or string
 * length; mapping validation must judge issues against [filteredSchema], not [detectedSchema], to avoid flagging
 * violations that a filter already removed.
 */
data class ImportJob(
  val id: ImportJobId,
  val userId: String,
  val installedAppId: InstalledAppId,
  val importDefinitionId: ImportDefinitionId,
  val status: ImportStatus,
  val payload: String,
  val detectedDataPaths: List<DataPath> = emptyList(),
  val detectedSchema: List<SchemaProperty> = emptyList(),
  val filteredSchema: List<SchemaProperty> = emptyList(),
  val triggeredBy: ImportTrigger = ImportTrigger.USER,
  /** Number of data objects saved by the accept run; null for legacy jobs accepted before counts were recorded (and for non-accepted jobs). */
  val addedCount: Int? = null,
  /** Number of pre-existing data objects removed by a replace-mode accept run (0 in add mode); null like [addedCount]. */
  val replacedCount: Int? = null,
  /** Number of mapped objects discarded as invalid by the accept run; null like [addedCount]. */
  val discardedCount: Int? = null,
  val createdAt: Instant,
  val lastChangedAt: Instant,
)
