package de.chrgroth.james.platform.domain.port.`in`.app

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.InstalledApp

/**
 * Lets a Developer create and manage test installations of their own Apps, without needing a real User account for them.
 * See docs/dev-tests.md ("Test Installations") for the concept.
 */
interface DeveloperTestInstallationPort {
  fun listTestInstallations(appId: String, developerId: String): Either<DomainError, List<InstalledApp>>
  /**
   * [developerId] (the developer's internal user id) is used for the app-ownership check; [userId] (the developer's
   * login username) is stamped onto the created [InstalledApp] so it is reachable through the same User-facing
   * lookups (see [de.chrgroth.james.platform.domain.port.in.app.UserAppStorePort.getInstalledApp]) real installations use.
   */
  fun createTestInstallation(appId: String, versionId: String, developerId: String, userId: String): Either<DomainError, InstalledApp>
  fun deleteTestInstallation(appId: String, installedAppId: String, developerId: String): Either<DomainError, Unit>
}
