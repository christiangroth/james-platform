package de.chrgroth.james.platform.domain.port.out.app

import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.VersionNumber

interface AppVersionRepositoryPort {
  fun findById(versionId: AppVersionId): AppVersion?
  fun findByAppIdAndVersionNumber(appId: AppId, versionNumber: VersionNumber): AppVersion?
  fun findAllByAppId(appId: AppId): List<AppVersion>
  fun findAllPublishedWithoutReleaseNotes(): List<AppVersion>
  fun delete(versionId: AppVersionId)
  fun deleteAll()
  fun save(version: AppVersion)
  fun renameToNewCollection()
}
