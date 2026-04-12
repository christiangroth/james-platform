package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.error.LoginError
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.port.`in`.user.LoginServicePort
import de.chrgroth.james.platform.domain.port.out.user.TokenEncryptionPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.security.identity.SecurityIdentity
import jakarta.annotation.security.PermitAll
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.FormParam
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.NewCookie
import jakarta.ws.rs.core.Response
import java.net.URI

@Path("/")
@ApplicationScoped
@Suppress("Unused")
class LoginResource {

  @Inject
  @Location("login.html")
  private lateinit var loginTemplate: Template

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var loginService: LoginServicePort

  @Inject
  private lateinit var tokenEncryption: TokenEncryptionPort

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  fun index(@QueryParam("error") error: String?): Response {
    if (!securityIdentity.isAnonymous) {
      return Response.temporaryRedirect(URI.create(dashboardUri(securityIdentity))).build()
    }
    return Response.ok(loginTemplate.data("errorMessage", error?.let { errorMessage(it) })).build()
  }

  @POST
  @Path("/login")
  @PermitAll
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  fun login(
    @FormParam("username") username: String?,
    @FormParam("password") password: String?,
  ): Response {
    if (username.isNullOrBlank() || password.isNullOrBlank()) {
      return Response.seeOther(URI.create("/?error=${LoginError.INVALID_CREDENTIALS.code}")).build()
    }
    val result = loginService.login(username, password)
    return result.fold(
      ifLeft = { Response.seeOther(URI.create("/?error=${LoginError.INVALID_CREDENTIALS.code}")).build() },
      ifRight = { user ->
        val encrypted = tokenEncryption.encrypt(user.username.value).getOrNull()
          ?: return Response.seeOther(URI.create("/?error=session")).build()
        val cookie = NewCookie.Builder(CookieAuthMechanism.COOKIE_NAME)
          .value(encrypted)
          .path("/")
          .httpOnly(true)
          .build()
        val primaryRole = when {
          user.roles.contains(UserRole.ADMIN) -> "admin"
          user.roles.contains(UserRole.DEVELOPER) -> "developer"
          else -> "user"
        }
        Response.seeOther(URI.create("/ui/$primaryRole/dashboard"))
          .cookie(cookie)
          .build()
      },
    )
  }

  @GET
  @Path("/logout")
  @PermitAll
  fun logout(): Response {
    val expiredCookie = NewCookie.Builder(CookieAuthMechanism.COOKIE_NAME)
      .value("")
      .path("/")
      .httpOnly(true)
      .maxAge(0)
      .build()
    return Response.temporaryRedirect(URI.create("/"))
      .cookie(expiredCookie)
      .build()
  }

  private fun dashboardUri(identity: SecurityIdentity): String {
    val roles = identity.roles
    return when {
      roles.contains(UserRole.ADMIN.name) -> "/ui/admin/dashboard"
      roles.contains(UserRole.DEVELOPER.name) -> "/ui/developer/dashboard"
      else -> "/ui/user/dashboard"
    }
  }

  private fun errorMessage(code: String): String = when (code) {
    LoginError.INVALID_CREDENTIALS.code -> "Invalid username or password. Please try again."
    else -> "An unexpected error occurred. Please try again."
  }
}
