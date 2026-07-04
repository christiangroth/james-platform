package de.chrgroth.james.platform.domain.port.out.app

import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId

interface AppDataRepositoryPort {
  fun findAllByInstalledAppId(installedAppId: InstalledAppId): List<AppData>
  fun findAllByInstalledAppIdAndEntityType(installedAppId: InstalledAppId, entityType: EntityDefinitionId): List<AppData>
  fun findById(id: AppDataId): AppData?
  fun save(appData: AppData)
  fun delete(id: AppDataId)
  fun deleteAllByInstalledAppId(installedAppId: InstalledAppId)
}
