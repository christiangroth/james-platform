package de.chrgroth.james.platform.domain.model.app

import java.time.Instant

@JvmInline
value class AppDataId(val value: String)

data class AppData(
  val id: AppDataId,
  val userId: String,
  val installedAppId: InstalledAppId,
  val appVersion: VersionNumber,
  val entityType: EntityDefinitionId,
  val objectVersion: Int,
  val createdAt: Instant,
  val lastChangedAt: Instant,
  val data: Map<String, String?>,
)
