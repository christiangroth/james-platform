package de.chrgroth.james.platform.adapter.`in`.http

import de.chrgroth.james.platform.domain.user.USER_ROLE_ADMIN
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import org.eclipse.microprofile.config.inject.ConfigProperty

// TODO tests for template routes
// TODO roles checks do not work, all is public
// TODO configure caching in non dev mode
// TODO add favicon

@PermitAll
@Path("/")
@Suppress("Unused")
class RootRedirect {

  @ConfigProperty(name = "quarkus.http.auth.form.landing-page")
  private lateinit var indexRedirectPath: String

  @Inject
  private lateinit var uriInfo: UriInfo

  @GET
  fun index(): Response {
    return Response.status(Response.Status.FOUND)
      .header(
        HttpHeaders.LOCATION,
        uriInfo.baseUriBuilder.path(indexRedirectPath).build().toString()
      )
      .build()
  }
}

@PermitAll
@Path("/ui/login")
@Suppress("Unused")
class LoginTemplate {

  @Location("login")
  private lateinit var login: Template

  @GET
  @Produces(MediaType.TEXT_HTML)
  fun login(): TemplateInstance {
    return login.data(Unit)
  }
}

@Authenticated
@Path("/ui")
@Suppress("Unused")
class UserTemplates {

  @Inject
  @Location("dashboard")
  private lateinit var dashboard: Template

  @GET
  @Path("/dashboard")
  @Produces(MediaType.TEXT_HTML)
  fun dashboard(): TemplateInstance {
    return dashboard.data(Unit)
  }
}

@RolesAllowed(value = [USER_ROLE_ADMIN])
@Path("/ui/admin")
@Suppress("Unused")
class AdminTemplates {

  @Inject
  @Location("admin/users")
  private lateinit var users: Template

  @GET
  @Path("/users")
  @Produces(MediaType.TEXT_HTML)
  fun users(): TemplateInstance {
    return users.data(Unit)
  }
}
