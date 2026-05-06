package de.chrgroth.james.platform.domain.port.out.app

import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId

interface InstalledAppRepositoryPort {
  fun findById(id: InstalledAppId): InstalledApp?
  fun findByUserIdAndAppId(userId: String, appId: AppId): InstalledApp?
  fun findAllByUserId(userId: String): List<InstalledApp>
  fun findAllByAppId(appId: AppId): List<InstalledApp>
  fun save(installedApp: InstalledApp)
}
