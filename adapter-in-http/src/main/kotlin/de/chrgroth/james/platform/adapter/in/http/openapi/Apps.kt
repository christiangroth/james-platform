package de.chrgroth.james.platform.adapter.`in`.http.openapi

import de.chrgroth.james.platform.adapter.`in`.http.DomainErrorsException
import de.chrgroth.james.platform.adapter.incoming.http.api.AppApi
import de.chrgroth.james.platform.adapter.incoming.http.api.model.App
import de.chrgroth.james.platform.domain.app.port.`in`.AppCommandPort
import de.chrgroth.james.platform.domain.app.port.`in`.AppQueryPort
import de.chrgroth.james.platform.domain.user.USER_ROLE_ADMIN
import de.chrgroth.james.platform.domain.user.USER_ROLE_DEVELOPER
import jakarta.annotation.security.RolesAllowed
import jakarta.inject.Inject

typealias ApiApp = App
typealias DomainApp = de.chrgroth.james.platform.domain.app.App

@RolesAllowed(value = [USER_ROLE_ADMIN, USER_ROLE_DEVELOPER])
@Suppress("Unused")
internal class AppsResource : AppApi {

  @Inject
  private lateinit var query: AppQueryPort

  @Inject
  private lateinit var command: AppCommandPort

  // TODO need List and defined sorting instead of Set?
  override fun getAllApps(): List<ApiApp> =
    query.all().fold({
      throw DomainErrorsException(it)
    }, { apps ->
      apps.map { it.toApiApp() }
    })

  // TODO return 201 Created
  // TODO needs to return at least appId
  override fun create() {
    command.create().tapInvalid {
      throw DomainErrorsException(it)
    }
  }
}

fun DomainApp.toApiApp() =
  ApiApp(
    id = id.value,
  )
