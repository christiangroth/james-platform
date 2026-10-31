package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.user.ApiTokenScope
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.server.ServerRequestFilter

/**
 * Role names granted to personal access token identities, one per [ApiTokenScope] currently in effect. Used with `@RolesAllowed` on the
 * `/api/v1` resources; cookie sessions only carry [de.chrgroth.james.platform.domain.model.user.UserRole] names, so the token API is reachable
 * with a token only.
 */
object ApiTokenRoles {
  const val USER_READ = "API_USER_READ"
  const val USER_WRITE = "API_USER_WRITE"
  const val DEVELOPER_READ = "API_DEVELOPER_READ"
  const val DEVELOPER_WRITE = "API_DEVELOPER_WRITE"
  const val DEVELOPER_RELEASE = "API_DEVELOPER_RELEASE"

  fun of(scope: ApiTokenScope): String = when (scope) {
    ApiTokenScope.USER_READ -> USER_READ
    ApiTokenScope.USER_WRITE -> USER_WRITE
    ApiTokenScope.DEVELOPER_READ -> DEVELOPER_READ
    ApiTokenScope.DEVELOPER_WRITE -> DEVELOPER_WRITE
    ApiTokenScope.DEVELOPER_RELEASE -> DEVELOPER_RELEASE
  }
}

/**
 * Default-deny for personal access tokens: a token-authenticated request is rejected unless it targets the dedicated token API below
 * [API_PATH_PREFIX], where each endpoint declares its required scope via `@RolesAllowed`. This keeps every UI endpoint (including token
 * management itself, so a token can never create, widen or re-activate tokens) out of reach for tokens.
 */
@ApplicationScoped
@Suppress("Unused")
class ApiTokenAccessFilter(
  private val identity: SecurityIdentity,
) {

  @ServerRequestFilter(preMatching = true)
  fun filter(requestContext: ContainerRequestContext): Response? {
    if (identity.getAttribute<String>(ApiTokenAuthMechanism.API_TOKEN_ID_ATTRIBUTE) == null) return null
    val path = "/" + requestContext.uriInfo.path.trimStart('/')
    // Dot segments are rejected outright so no path can start with the prefix yet resolve elsewhere.
    if (path.startsWith(API_PATH_PREFIX) && !path.contains("..")) return null
    return Response.status(Response.Status.FORBIDDEN)
      .type(MediaType.APPLICATION_JSON)
      .entity(ApiErrorResponse("FORBIDDEN", "Endpoint is not available for API token access"))
      .build()
  }

  companion object {
    const val API_PATH_PREFIX = "/api/"
  }
}
