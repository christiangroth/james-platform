package de.chrgroth.james.platform.domain.port.out.app

import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.TimeGranularity
import de.chrgroth.james.platform.domain.model.app.VersionNumber

interface AppVersionRepositoryPort {
  fun findById(versionId: AppVersionId): AppVersion?
  fun findByAppIdAndVersionNumber(appId: AppId, versionNumber: VersionNumber): AppVersion?
  fun findAllByAppId(appId: AppId): List<AppVersion>
  fun findAll(): List<AppVersion>
  fun findAllPublishedWithoutReleaseNotes(): List<AppVersion>
  fun delete(versionId: AppVersionId)
  fun deleteAll()
  fun save(version: AppVersion)
  fun renameToNewCollection()

  /**
   * One-time migration (see ADR 0017): rewrites every legacy `DURATION`-typed property (scalar, LIST item, or nested at any depth inside an
   * OBJECT property, and computed properties) across all stored [AppVersion]s — published and draft — to `LONG`, with a `TIME` [PropertyUnit]
   * (storage granularity SECONDS) attached where the domain model supports it. `MinDuration`/`MaxDuration` constraints are converted to
   * `MinLong`/`MaxLong` (value in seconds). Operates on raw storage documents, not domain objects, since a stored `DURATION` value can no
   * longer be deserialized once the enum constant is removed. Idempotent: a second run finds nothing left to migrate.
   * Returns the value locations of every migrated (non-computed) property, grouped by entity, so the AppData migration can convert the
   * corresponding stored values from legacy duration text to seconds.
   */
  fun migrateDurationProperties(defaultGranularity: TimeGranularity): List<DurationPropertyLocation>
}

/**
 * The location of a formerly `DURATION`-typed value within an entity's AppData, as found by [AppVersionRepositoryPort.migrateDurationProperties].
 * [path] is the chain of property ids from the top-level property down through nested OBJECT properties to the migrated property itself.
 * [isListItem] is true if the migrated property is (or is nested under) a `LIST` property whose item type was `DURATION`.
 */
data class DurationPropertyLocation(
  val entityDefinitionId: EntityDefinitionId,
  val path: List<PropertyId>,
  val isListItem: Boolean,
)
