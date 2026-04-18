package de.chrgroth.james.platform.domain.model.app

import java.time.Instant

@JvmInline
value class InstalledAppId(val value: String)

data class InstalledApp(
  val id: InstalledAppId,
  val userId: String,
  val appId: AppId,
  val installedVersionNumber: VersionNumber,
  val installedAt: Instant,
)
