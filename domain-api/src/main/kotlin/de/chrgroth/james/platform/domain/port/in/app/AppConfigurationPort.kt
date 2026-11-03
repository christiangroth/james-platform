package de.chrgroth.james.platform.domain.port.`in`.app

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId

/**
 * The App configuration (ADR 0027): an App Version may mark one Entity as its configuration, a singleton with exactly one object per
 * installation. The object is created from the property defaults on install and on upgrading to a version introducing the configuration.
 */
interface AppConfigurationPort {

  /** Creates the configuration object of [installedAppId] from the defaults, if its version defines a configuration Entity and it has none yet. */
  fun ensureConfiguration(installedAppId: InstalledAppId)

  /** The configuration object of the [userId]'s installation, created from the defaults if it is still missing. */
  fun getConfiguration(userId: String, installedAppId: String): Either<DomainError, AppData>

  /**
   * The stored values (property id to raw value, like `AppData.data`) of the configuration of [installedApp] on [version], read-only: the
   * defaults if the object does not exist yet, an empty map if [version] defines no configuration Entity.
   */
  fun configurationValues(installedApp: InstalledApp, version: AppVersion): Map<String, String?>
}
