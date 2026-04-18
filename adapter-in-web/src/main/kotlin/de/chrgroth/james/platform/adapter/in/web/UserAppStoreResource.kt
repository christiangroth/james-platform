package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.error.UserAppStoreError
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.net.URI

@Path("/ui")
@ApplicationScoped
@Authenticated
@Suppress("Unused", "TooManyFunctions")
class UserAppStoreResource {

  @Inject
  @Location("ui/user/app-store.html")
  private lateinit var appStoreTemplate: Template

  @Inject
  @Location("ui/user/app-store-detail.html")
  private lateinit var appStoreDetailTemplate: Template

  @Inject
  @Location("ui/user/app-detail.html")
  private lateinit var appDetailTemplate: Template

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var userAppStore: UserAppStorePort

  @GET
  @Path("/user/app-store")
  @Produces(MediaType.TEXT_HTML)
  fun appStore(): Response {
    val userId = securityIdentity.principal.name
    val allApps = userAppStore.listAllPublishedApps()
    val installedAppIds = userAppStore.getInstalledApps(userId).map { it.installedApp.appId.value }.toSet()
    return Response.ok(
      appStoreTemplate
        .data("apps", allApps)
        .data("installedAppIds", installedAppIds),
    ).build()
  }

  @GET
  @Path("/user/app-store/apps/{appId}")
  @Produces(MediaType.TEXT_HTML)
  fun appStoreDetail(@PathParam("appId") appId: String): Response {
    val userId = securityIdentity.principal.name
    return userAppStore.getPublishedApp(appId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/user/app-store")).build() },
      ifRight = { detail ->
        val installedApps = userAppStore.getInstalledApps(userId)
        val installed = installedApps.find { it.installedApp.appId.value == appId }
        Response.ok(
          appStoreDetailTemplate
            .data("detail", detail)
            .data("installedApp", installed),
        ).build()
      },
    )
  }

  @POST
  @Path("/user/app-store/apps/{appId}/install")
  @Produces(MediaType.APPLICATION_JSON)
  fun installApp(@PathParam("appId") appId: String): Response {
    val userId = securityIdentity.principal.name
    return userAppStore.installApp(userId, appId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appStoreErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, "App installed.", "/ui/user/dashboard")).build() },
    )
  }

  @POST
  @Path("/user/apps/{installedAppId}/upgrade")
  @Produces(MediaType.APPLICATION_JSON)
  fun upgradeApp(@PathParam("installedAppId") installedAppId: String): Response {
    val userId = securityIdentity.principal.name
    return userAppStore.upgradeApp(userId, installedAppId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appStoreErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, "App upgraded.", "/ui/user/dashboard")).build() },
    )
  }

  @GET
  @Path("/user/apps/{installedAppId}")
  @Produces(MediaType.TEXT_HTML)
  fun installedAppDetail(@PathParam("installedAppId") installedAppId: String): Response {
    val userId = securityIdentity.principal.name
    val installedApps = userAppStore.getInstalledApps(userId)
    val info = installedApps.find { it.installedApp.id.value == installedAppId }
      ?: return Response.seeOther(URI.create("/ui/user/dashboard")).build()
    return Response.ok(
      appDetailTemplate.data("info", info),
    ).build()
  }

  private fun appStoreErrorMessage(code: String): String = when (code) {
    UserAppStoreError.APP_NOT_FOUND.code -> "App not found."
    UserAppStoreError.NO_PUBLISHED_VERSION.code -> "No published version available."
    UserAppStoreError.ALREADY_INSTALLED.code -> "App is already installed."
    UserAppStoreError.NOT_INSTALLED.code -> "App is not installed."
    UserAppStoreError.INSTALLED_APP_NOT_FOUND.code -> "Installed app not found."
    UserAppStoreError.ALREADY_UP_TO_DATE.code -> "App is already up to date."
    else -> "An unexpected error occurred. Please try again."
  }
}
