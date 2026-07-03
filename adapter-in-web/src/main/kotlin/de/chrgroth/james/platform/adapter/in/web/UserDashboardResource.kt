package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.port.`in`.app.AppDataPort
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

data class EntityDataCount(
  val entityName: String,
  val count: Int,
)

data class DashboardInstalledApp(
  val installedAppId: String,
  val appName: String,
  val installedVersion: AppVersion,
  val latestVersion: AppVersion,
  val entityCounts: List<EntityDataCount>,
)

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

  @Inject
  private lateinit var appData: AppDataPort

  @GET
  @Path("/user/dashboard")
  @Authenticated
  @BlockAdminAccess
  @Produces(MediaType.TEXT_HTML)
  fun userDashboard(): Any {
    val userId = securityIdentity.principal.name
    val installedApps = userAppStore.getInstalledApps(userId)
    val dashboardApps = installedApps.map { info ->
      val allAppData = appData.listAppData(userId, info.installedAppId).getOrNull() ?: emptyList()
      val countsByEntity = allAppData.groupingBy { it.entityType.value }.eachCount()
      val entityCounts = info.installedVersion.entityDefinitions.map { entity ->
        EntityDataCount(
          entityName = entity.name,
          count = countsByEntity.getOrDefault(entity.id.value, 0),
        )
      }
      DashboardInstalledApp(
        installedAppId = info.installedAppId,
        appName = info.appName,
        installedVersion = info.installedVersion,
        latestVersion = info.latestVersion,
        entityCounts = entityCounts,
      )
    }
    return userDashboardTemplate
      .data("username", userId)
      .data("installedApps", dashboardApps)
  }

  @GET
  @Path("/admin/dashboard")
  @RolesAllowed("ADMIN")
  @Produces(MediaType.TEXT_HTML)
  fun adminDashboard() = adminDashboardTemplate.data("userCount", userRepository.findAll().size)
}

