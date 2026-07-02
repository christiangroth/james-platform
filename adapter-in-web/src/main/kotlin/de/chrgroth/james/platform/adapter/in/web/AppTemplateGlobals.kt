package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.adapter.`in`.web.i18n.Language
import de.chrgroth.james.platform.domain.model.user.UserRole
import io.quarkus.qute.EngineBuilder
import io.quarkus.security.identity.SecurityIdentity
import io.vertx.ext.web.RoutingContext
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

  @Inject
  private lateinit var routingContext: RoutingContext

  private val assetVersion: String = System.currentTimeMillis().toString()

  fun onEngineBuilder(@Observes builder: EngineBuilder) {
    builder.addTemplateInstanceInitializer { instance ->
      instance.data("appBuildVersion", version)
      instance.data("assetVersion", assetVersion)
      val isAdmin = runCatching { securityIdentity.roles?.contains(UserRole.ADMIN.name) ?: false }.getOrDefault(false)
      instance.data("isAdmin", isAdmin)
      val isDeveloper = runCatching { securityIdentity.roles?.contains(UserRole.DEVELOPER.name) ?: false }.getOrDefault(false)
      instance.data("isDeveloper", isDeveloper)
      val isMonitoring = runCatching { securityIdentity.roles?.contains(UserRole.MONITORING.name) ?: false }.getOrDefault(false)
      instance.data("isMonitoring", isMonitoring)
      val language = currentLanguage()
      instance.setLocale(language.locale)
      instance.data("currentLanguage", language.code)
    }
  }

  private fun currentLanguage(): Language {
    val cookieValue = runCatching { routingContext.request().getCookie(Language.COOKIE_NAME)?.value }.getOrNull()
    return Language.fromCode(cookieValue)
  }
}
