package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.port.`in`.user.ApiTokenPort
import io.quarkus.security.identity.IdentityProviderManager
import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.security.runtime.QuarkusSecurityIdentity
import io.quarkus.vertx.http.runtime.security.ChallengeData
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.infrastructure.Infrastructure
import io.vertx.ext.web.RoutingContext
import jakarta.enterprise.context.ApplicationScoped
import java.security.Principal
import java.util.Optional

/**
 * Authenticates requests carrying `Authorization: Bearer <personal access token>`. Runs before [CookieAuthMechanism]; requests without a Bearer
 * header fall through to the cookie session. The principal is the token owner's username (so all User/Developer ports work unchanged), the
 * roles are the token's effective scopes (see [ApiTokenRoles]) - never the owner's own roles. Which endpoints a token may reach is enforced by
 * [ApiTokenAccessFilter] (default-deny outside `/api/`) and `@RolesAllowed` on the `/api/v1` resources.
 */
@ApplicationScoped
@Suppress("Unused")
class ApiTokenAuthMechanism(
  private val apiTokens: ApiTokenPort,
) : HttpAuthenticationMechanism {

  override fun authenticate(context: RoutingContext, identityProviderManager: IdentityProviderManager): Uni<SecurityIdentity> {
    val plainToken = bearerToken(context) ?: return Uni.createFrom().optional(Optional.empty())
    // Token lookup hits MongoDB, so it must not run on the event loop.
    return Uni.createFrom().item { apiTokens.authenticate(plainToken) }
      .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
      .map { authenticated ->
        authenticated?.let {
          QuarkusSecurityIdentity.builder()
            .setPrincipal(Principal { authenticated.owner.username.value })
            .setAnonymous(false)
            .addRoles(authenticated.effectiveScopes.map { ApiTokenRoles.of(it) }.toSet())
            .addAttribute(API_TOKEN_ID_ATTRIBUTE, authenticated.token.id.value)
            .addAttribute(API_TOKEN_USER_ID_ATTRIBUTE, authenticated.owner.id.value)
            .addAttribute(API_TOKEN_APP_IDS_ATTRIBUTE, authenticated.token.appIds)
            .build()
        }
      }
  }

  override fun getChallenge(context: RoutingContext): Uni<ChallengeData?> =
    if (bearerToken(context) != null || context.normalizedPath().startsWith(ApiTokenAccessFilter.API_PATH_PREFIX)) {
      Uni.createFrom().item(ChallengeData(UNAUTHORIZED_STATUS, "WWW-Authenticate", "Bearer"))
    } else {
      // No Bearer header outside the API: let the cookie mechanism's challenge (redirect to login) apply.
      Uni.createFrom().nullItem()
    }

  override fun getPriority(): Int = HttpAuthenticationMechanism.DEFAULT_PRIORITY + 1

  private fun bearerToken(context: RoutingContext): String? =
    context.request().getHeader(AUTHORIZATION_HEADER)
      ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
      ?.substring(BEARER_PREFIX.length)
      ?.trim()
      ?.takeIf { it.isNotEmpty() }

  companion object {
    const val API_TOKEN_ID_ATTRIBUTE = "api-token-id"
    const val API_TOKEN_USER_ID_ATTRIBUTE = "api-token-user-id"
    const val API_TOKEN_APP_IDS_ATTRIBUTE = "api-token-app-ids"
    private const val AUTHORIZATION_HEADER = "Authorization"
    private const val BEARER_PREFIX = "Bearer "
    private const val UNAUTHORIZED_STATUS = 401
  }
}
