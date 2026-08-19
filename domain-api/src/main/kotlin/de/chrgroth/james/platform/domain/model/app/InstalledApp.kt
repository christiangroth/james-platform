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
  /** Marks a Developer-owned test installation (see docs/dev-tests.md), excluded from normal User-facing listings, sharing, and Report data sources. */
  val isTest: Boolean = false,
  /** Set only for test installations, so they can pin a DRAFT version (which has no [installedVersionNumber] yet). Real installations resolve their version by number instead. */
  val installedVersionId: AppVersionId? = null,
  /**
   * Set while a background test data generation run (see [de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent.GenerateTestData])
   * is in flight for this entity, following the same "status field the UI reflects on next reload" pattern ADR 0019
   * established for `ImportJob.status = ACCEPTING`. Cleared again once the run finishes, regardless of outcome.
   */
  val generatingEntityId: EntityDefinitionId? = null,
)
