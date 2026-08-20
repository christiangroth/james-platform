package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort

/** Test installations pin their version by id since a DRAFT version has no [InstalledApp.installedVersionNumber] yet. */
internal fun resolveInstalledAppVersion(installedApp: InstalledApp, appVersionRepository: AppVersionRepositoryPort): AppVersion? =
  installedApp.installedVersionId?.let { appVersionRepository.findById(it) }
    ?: appVersionRepository.findByAppIdAndVersionNumber(installedApp.appId, installedApp.installedVersionNumber)
