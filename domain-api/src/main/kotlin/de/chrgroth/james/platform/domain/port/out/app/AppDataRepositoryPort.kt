package de.chrgroth.james.platform.domain.port.out.app

import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId

interface AppDataRepositoryPort {
  fun findAllByInstalledAppId(installedAppId: InstalledAppId): List<AppData>
  fun findAllByInstalledAppIdAndEntityType(installedAppId: InstalledAppId, entityType: EntityDefinitionId): List<AppData>

  /** Returns every stored AppData object across all installed apps. Acceptable only for one-time startup migrations, never for request handling. */
  fun findAll(): List<AppData>
  fun findById(id: AppDataId): AppData?
  fun save(appData: AppData)
  fun delete(id: AppDataId)
  fun deleteAllByInstalledAppId(installedAppId: InstalledAppId)
  fun deleteAllByInstalledAppIdAndEntityType(installedAppId: InstalledAppId, entityType: EntityDefinitionId)
}
