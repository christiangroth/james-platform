package de.chrgroth.james.platform.domain.model.imports

import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import java.time.Instant

@JvmInline
value class ImportJobId(val value: String)

enum class ImportStatus {
  DOWNLOADED,
  DATA_IDENTIFIED,
  READY,
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
 * A single fetch-to-mapping-to-import run, targeting a fixed [installedAppId] and [targetEntityDefinitionId] and
 * fetched through a reusable [ImportConnection] (referenced by [connectionId]). Unlike the connection, a job only
 * holds the data snapshot for one point in time and is cleaned up automatically when it stays inactive too long.
 *
 * [urlPostfix], when set, is appended to the connection's base URL to form this job's actual fetch URL (see
 * [resolveImportUrl]); left `null`, the job fetches from the connection's base URL unchanged.
 *
 * [detectedSchema] is derived once from the raw, unfiltered records at [selectedDataPath] and stays unchanged
 * afterwards - it is the field reference panel shown across the Filter and Mapping steps. [filteredSchema] is
 * recomputed and persisted alongside [filterRules] whenever they change (see `ImportService.updateFilter`), since
 * filter rules can change which records survive and thus a property's mandatory-ness, value range, or string length;
 * mapping validation must judge issues against [filteredSchema], not [detectedSchema], to avoid flagging violations
 * that a filter already removed.
 */
data class ImportJob(
  val id: ImportJobId,
  val userId: String,
  val installedAppId: InstalledAppId,
  val connectionId: ImportConnectionId,
  val urlPostfix: String? = null,
  val targetEntityDefinitionId: EntityDefinitionId,
  val status: ImportStatus,
  val payload: String,
  val detectedDataPaths: List<DataPath> = emptyList(),
  val selectedDataPath: String? = null,
  val detectedSchema: List<SchemaProperty> = emptyList(),
  val filteredSchema: List<SchemaProperty> = emptyList(),
  val filterRules: List<FilterRule> = emptyList(),
  val mapping: Mapping? = null,
  val createdAt: Instant,
  val lastChangedAt: Instant,
)
