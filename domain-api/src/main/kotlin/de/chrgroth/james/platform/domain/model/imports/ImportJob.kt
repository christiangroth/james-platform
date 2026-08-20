package de.chrgroth.james.platform.domain.model.imports

import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import java.time.Instant

@JvmInline
value class ImportJobId(val value: String)

enum class ImportStatus {
  DOWNLOADED,
  DATA_IDENTIFIED,
  READY,

  /** Accept was triggered and enqueued via the outbox; the job is kept around until the background dispatcher deletes it on success. */
  ACCEPTING,
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
 * cleaned up automatically when it stays inactive too long; accepting it (see `ImportService.handle`) deletes the
 * job but leaves its definition in place for reuse - see docs/adr/0021-import-definition-job-split.md.
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
  val createdAt: Instant,
  val lastChangedAt: Instant,
)
