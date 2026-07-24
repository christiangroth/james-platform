package de.chrgroth.james.platform.domain.model.imports

import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import java.time.Instant

@JvmInline
value class ImportDocumentId(val value: String)

enum class ImportStatus {
  DOWNLOADED,
  DATA_IDENTIFIED,
}

data class DataPath(
  val path: String,
  val size: Int,
)

enum class SchemaPropertyType {
  STRING,
  NUMBER,
  BOOLEAN,
  OBJECT,
  ARRAY,
  NULL,
}

data class SchemaProperty(
  val path: String,
  val typeCounts: Map<SchemaPropertyType, Int>,
  val mandatory: Boolean,
)

data class ImportDocument(
  val id: ImportDocumentId,
  val userId: String,
  val installedAppId: InstalledAppId,
  val sourceUrl: String,
  val encryptedBearerToken: String,
  val status: ImportStatus,
  val payload: String,
  val detectedDataPaths: List<DataPath> = emptyList(),
  val selectedDataPath: String? = null,
  val detectedSchema: List<SchemaProperty> = emptyList(),
  val createdAt: Instant,
  val lastChangedAt: Instant,
)
