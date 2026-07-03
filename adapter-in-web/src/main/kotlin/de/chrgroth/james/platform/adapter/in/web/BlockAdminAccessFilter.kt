package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.user.UserRole
import io.quarkus.security.identity.SecurityIdentity
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.NameBinding
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider

/**
 * Denies access regardless of other roles held, for endpoints that must stay off-limits to the ADMIN role
 * even though it isn't a plain allow-list case (e.g. an admin who also holds USER/DEVELOPER/MONITORING roles).
 */
@NameBinding
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BlockAdminAccess

@Provider
@BlockAdminAccess
@Priority(Priorities.AUTHORIZATION)
@ApplicationScoped
@Suppress("Unused")
class BlockAdminAccessFilter : ContainerRequestFilter {

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  override fun filter(requestContext: ContainerRequestContext) {
    val isAdmin = runCatching { securityIdentity.roles?.contains(UserRole.ADMIN.name) ?: false }.getOrDefault(false)
    if (isAdmin) {
      requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build())
    }
  }
}
