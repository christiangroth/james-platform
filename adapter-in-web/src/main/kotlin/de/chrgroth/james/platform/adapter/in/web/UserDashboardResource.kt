package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/ui")
@ApplicationScoped
@Suppress("Unused")
class UserDashboardResource {

  @Inject
  @Location("ui/user/dashboard.html")
  private lateinit var userDashboardTemplate: Template

  @Inject
  @Location("ui/admin/dashboard.html")
  private lateinit var adminDashboardTemplate: Template

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var userRepository: UserRepositoryPort

  @Inject
  private lateinit var userAppStore: UserAppStorePort

  @GET
  @Path("/user/dashboard")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun userDashboard(): Any {
    val userId = securityIdentity.principal.name
    val installedApps = userAppStore.getInstalledApps(userId)
    return userDashboardTemplate
      .data("username", userId)
      .data("installedApps", installedApps)
  }

  @GET
  @Path("/admin/dashboard")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun adminDashboard() = adminDashboardTemplate.data("userCount", userRepository.findAll().size)
}

