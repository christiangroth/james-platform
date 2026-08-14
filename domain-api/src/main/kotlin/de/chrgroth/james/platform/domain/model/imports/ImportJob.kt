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
 */
data class ImportJob(
  val id: ImportJobId,
  val userId: String,
  val installedAppId: InstalledAppId,
  val connectionId: ImportConnectionId,
  val targetEntityDefinitionId: EntityDefinitionId,
  val status: ImportStatus,
  val payload: String,
  val detectedDataPaths: List<DataPath> = emptyList(),
  val selectedDataPath: String? = null,
  val detectedSchema: List<SchemaProperty> = emptyList(),
  val mapping: Mapping? = null,
  val createdAt: Instant,
  val lastChangedAt: Instant,
)
