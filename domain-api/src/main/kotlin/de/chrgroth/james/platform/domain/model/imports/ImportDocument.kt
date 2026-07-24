package de.chrgroth.james.platform.domain.model.imports

import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import java.time.Instant

@JvmInline
value class ImportDocumentId(val value: String)

enum class ImportStatus {
  DOWNLOADED,
}

data class ImportDocument(
  val id: ImportDocumentId,
  val userId: String,
  val installedAppId: InstalledAppId,
  val sourceUrl: String,
  val encryptedBearerToken: String,
  val status: ImportStatus,
  val payload: String,
  val createdAt: Instant,
  val lastChangedAt: Instant,
)
