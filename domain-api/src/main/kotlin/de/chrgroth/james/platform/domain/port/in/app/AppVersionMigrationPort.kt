package de.chrgroth.james.platform.domain.port.`in`.app

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.VersionNumber

/** Result of running a single Entity's [EntityDefinition.migrationScript], see docs/app-version-migration.md section 5.3. */
sealed interface MigrationScriptResult {
  data class Success(val data: Map<String, String?>) : MigrationScriptResult
  data class Failure(val reason: String) : MigrationScriptResult
}

/**
 * Executes Developer-authored Entity migration scripts (distinct from the platform-internal, one-time [AppDataMigrationPort]) as part of the
 * installation upgrade flow. See docs/app-version-migration.md.
 */
interface AppVersionMigrationPort {

  /** Runs [newEntity]'s migration script (if any) against [data], given [previousEntity] as the entity definition it is migrating from. */
  fun runScript(previousEntity: EntityDefinition, newEntity: EntityDefinition, data: Map<String, String?>): MigrationScriptResult

  /**
   * Applies every pending Entity migration script across all published Versions strictly between [fromVersion] (exclusive) and [toVersion]
   * (inclusive), in publish order, to the AppData of [installedAppId]. Only objects whose `lastValidatedWithVersion` is older than a given
   * migration's Version are migrated. On success (including when there was nothing to migrate) returns [Unit]; nothing is persisted for this
   * installation unless every pending migration succeeds and re-validates. On failure, returns the [DomainError] describing the first
   * failing object and persists nothing for this installation.
   */
  fun migrateInstallation(installedAppId: InstalledAppId, appId: AppId, fromVersion: VersionNumber, toVersion: VersionNumber): Either<DomainError, Unit>

  /**
   * Dry-runs each (previous, new) Entity pair in [entityMigrations] against every existing AppData row of [appId], across all installations,
   * without persisting anything. Used by `AppVersionManagementService.publishVersion()`/`computeVersionBump()` (see
   * docs/app-version-migration.md section 5.4) to check whether a migration neutralizes an otherwise-breaking change before a Version is
   * published. Returns [Unit] on success (including when there is no matching data); on failure, the [DomainError] describing the first
   * failing object.
   */
  fun dryRunMigration(appId: AppId, entityMigrations: List<Pair<EntityDefinition, EntityDefinition>>): Either<DomainError, Unit>
}
