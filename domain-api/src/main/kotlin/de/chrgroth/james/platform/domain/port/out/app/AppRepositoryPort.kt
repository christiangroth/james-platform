package de.chrgroth.james.platform.domain.port.out.app

import de.chrgroth.james.platform.domain.model.app.App
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppName

interface AppRepositoryPort {
  fun findById(appId: AppId): App?
  fun findByName(name: AppName): App?
  fun findAll(): List<App>
  fun findAllByDeveloperId(developerId: String): List<App>
  fun save(app: App)
}
