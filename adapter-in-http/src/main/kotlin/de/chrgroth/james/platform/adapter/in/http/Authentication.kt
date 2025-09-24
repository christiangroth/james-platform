package de.chrgroth.james.platform.adapter.`in`.http

import de.chrgroth.james.platform.domain.user.USER_ROLE_ADMIN
import de.chrgroth.james.platform.domain.user.USER_ROLE_DEVELOPER
import de.chrgroth.james.platform.domain.user.User
import de.chrgroth.james.platform.domain.user.port.`in`.UserQueryPort
import io.quarkus.security.Authenticated
import io.quarkus.security.AuthenticationFailedException
import io.quarkus.security.identity.AuthenticationRequestContext
import io.quarkus.security.identity.IdentityProvider
import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.security.identity.request.TrustedAuthenticationRequest
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest
import io.quarkus.security.runtime.QuarkusPrincipal
import io.quarkus.security.runtime.QuarkusSecurityIdentity
import io.quarkus.security.runtime.SecurityIdentityAssociation
import io.smallrye.mutiny.Uni
import jakarta.annotation.security.PermitAll
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.NewCookie
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.resteasy.reactive.RestResponse
import java.time.Instant
import java.util.Date

@ApplicationScoped
@Suppress("Unused")
internal class JamesFormLoginIdentityProvider : IdentityProvider<UsernamePasswordAuthenticationRequest> {

  @Inject
  private lateinit var port: UserQueryPort

  override fun getRequestType(): Class<UsernamePasswordAuthenticationRequest> =
    UsernamePasswordAuthenticationRequest::class.java

  override fun authenticate(
    request: UsernamePasswordAuthenticationRequest,
    context: AuthenticationRequestContext,
  ): Uni<SecurityIdentity> =
    context.runBlocking {
      port.authenticate(request.username, request.password.password.concatToString()).fold({
        throw AuthenticationFailedException()
      }, { user ->
        user.toPrincipal()
      })
    }

  companion object : KLogging()
}

@ApplicationScoped
@Suppress("Unused")
internal class JamesAuthCookieIdentityProvider : IdentityProvider<TrustedAuthenticationRequest> {

  @Inject
  private lateinit var port: UserQueryPort

  override fun getRequestType(): Class<TrustedAuthenticationRequest> =
    TrustedAuthenticationRequest::class.java

  override fun authenticate(
    request: TrustedAuthenticationRequest,
    context: AuthenticationRequestContext,
  ): Uni<SecurityIdentity> =
    context.runBlocking {
      port.byUsername(request.principal).fold({
        throw AuthenticationFailedException()
      }, { user ->
        user?.toPrincipal() ?: throw AuthenticationFailedException()
      })
    }

  companion object : KLogging()
}

private fun User.toPrincipal(): QuarkusSecurityIdentity =
  QuarkusSecurityIdentity.builder()
    .setPrincipal(QuarkusPrincipal(this.username))
    .setAnonymous(false)
    .addRoles(this.roles.map { it.value }.toSet())
    .build()

@Authenticated
@Path("/auth/login-redirect")
@Suppress("Unused")
class AfterLoginRedirectHandler {

  @Inject
  private lateinit var identity: SecurityIdentityAssociation

  @GET
  @Produces(MediaType.TEXT_HTML)
  fun afterRedirectLogin(@Context uriInfo: UriInfo): RestResponse<Void> {
    val roles = identity.identity.roles
    val redirectPath = when {
      roles.contains(USER_ROLE_ADMIN) -> "admin/users"
      roles.contains(USER_ROLE_DEVELOPER) -> "developer/dashboard"
      else -> "dashboard"
    }

    return RestResponse.seeOther(
      uriInfo.baseUriBuilder.path("ui").path(redirectPath).build()
    )
  }
}

@PermitAll
@Path("/auth/logout")
@Suppress("Unused")
internal class AuthenticationResource {

  @ConfigProperty(name = "quarkus.http.auth.form.cookie-name")
  private lateinit var cookieName: String

  @ConfigProperty(name = "quarkus.http.auth.form.login-page")
  private lateinit var loginPagePath: String

  @Inject
  private lateinit var uriInfo: UriInfo

  @GET
  fun logout(): Response {
    val cookieInvalidation = NewCookie.Builder(cookieName)
      .path("/")
      .httpOnly(true)
      .sameSite(NewCookie.SameSite.STRICT)
      .maxAge(0)
      .expiry(Date.from(Instant.EPOCH))
      .build()

    return Response.status(Response.Status.FOUND)
      .header(
        HttpHeaders.LOCATION,
        uriInfo.baseUriBuilder.path(loginPagePath).build().toString()
      )
      .cookie(cookieInvalidation)
      .build()
  }
}
