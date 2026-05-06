package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.user.UserRole
import io.quarkus.qute.EngineBuilder
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class AppTemplateGlobals {

  @field:ConfigProperty(name = "quarkus.application.version")
  lateinit var version: String

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  fun onEngineBuilder(@Observes builder: EngineBuilder) {
    builder.addTemplateInstanceInitializer { instance ->
      instance.data("appBuildVersion", version)
      val isAdmin = runCatching { securityIdentity.roles?.contains(UserRole.ADMIN.name) ?: false }.getOrDefault(false)
      instance.data("isAdmin", isAdmin)
      val isDeveloper = runCatching { securityIdentity.roles?.contains(UserRole.DEVELOPER.name) ?: false }.getOrDefault(false)
      instance.data("isDeveloper", isDeveloper)
      val isMonitoring = runCatching { securityIdentity.roles?.contains(UserRole.MONITORING.name) ?: false }.getOrDefault(false)
      instance.data("isMonitoring", isMonitoring)
    }
  }
}
