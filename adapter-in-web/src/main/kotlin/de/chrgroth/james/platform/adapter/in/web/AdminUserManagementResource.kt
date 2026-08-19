package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.adapter.`in`.web.i18n.AdminMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.AppMessages
import de.chrgroth.james.platform.domain.error.UserAdminError
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.port.`in`.user.AdminUserManagementPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.security.identity.SecurityIdentity
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.FormParam
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

data class ApiResult(val ok: Boolean, val message: String)

data class UserStatusResponse(val stillExists: Boolean)

@Path("/ui/admin/users")
@ApplicationScoped
@RolesAllowed("ADMIN")
@Suppress("Unused")
class AdminUserManagementResource {

  @Inject
  @Location("ui/admin/users.html")
  private lateinit var usersTemplate: Template

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var adminUserManagement: AdminUserManagementPort

  @Inject
  private lateinit var msg: AppMessages

  @Inject
  private lateinit var adminMsg: AdminMessages

  @Inject
  private lateinit var httpResponseMetrics: HttpResponseMetrics

  @GET
  @Produces(MediaType.TEXT_HTML)
  fun users(): Any = httpResponseMetrics.timed("page.admin.users") { renderUsers() }

  @GET
  @Path("/table")
  @Produces(MediaType.TEXT_HTML)
  fun usersTable(): Any = httpResponseMetrics.timed("fragment.admin.users-table") { renderUsersTable() }

  @PUT
  @Path("/{username}")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun createUser(
    @PathParam("username") username: String,
    @FormParam("password") password: String?,
  ): Response = httpResponseMetrics.timed("rest.admin.user-create") {
    if (password.isNullOrBlank()) {
      return@timed Response.ok(ApiResult(false, errorMessage(UserAdminError.BLANK_INPUT.code))).build()
    }
    val callingUsername = securityIdentity.principal.name
    adminUserManagement.createUser(username, password, callingUsername).fold(
      ifLeft = { error -> Response.ok(ApiResult(false, errorMessage(error.code))).build() },
      ifRight = { Response.ok(ApiResult(true, adminMsg.adminUserCreatedMessage())).build() },
    )
  }

  @POST
  @Path("/{username}/activation")
  @Produces(MediaType.APPLICATION_JSON)
  fun activateUser(@PathParam("username") username: String): Response = httpResponseMetrics.timed("rest.admin.user-activate") {
    adminUserManagement.activateUser(username).fold(
      ifLeft = { error -> Response.ok(ApiResult(false, errorMessage(error.code))).build() },
      ifRight = { Response.ok(ApiResult(true, adminMsg.adminUserActivatedMessage())).build() },
    )
  }

  @DELETE
  @Path("/{username}/activation")
  @Produces(MediaType.APPLICATION_JSON)
  fun deactivateUser(@PathParam("username") username: String): Response = httpResponseMetrics.timed("rest.admin.user-deactivate") {
    val callingUsername = securityIdentity.principal.name
    adminUserManagement.deactivateUser(username, callingUsername).fold(
      ifLeft = { error -> Response.ok(ApiResult(false, errorMessage(error.code))).build() },
      ifRight = { Response.ok(ApiResult(true, adminMsg.adminUserDeactivatedMessage())).build() },
    )
  }

  @POST
  @Path("/{username}/password")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun setPassword(
    @PathParam("username") username: String,
    @FormParam("newPassword") newPassword: String?,
  ): Response = httpResponseMetrics.timed("rest.admin.user-set-password") {
    if (newPassword.isNullOrBlank()) {
      return@timed Response.ok(ApiResult(false, errorMessage("password-blank"))).build()
    }
    adminUserManagement.setPassword(username, newPassword).fold(
      ifLeft = { error -> Response.ok(ApiResult(false, errorMessage(error.code))).build() },
      ifRight = { Response.ok(ApiResult(true, adminMsg.adminPasswordSetMessage())).build() },
    )
  }

  @POST
  @Path("/{username}/roles")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun setRoles(
    @PathParam("username") username: String,
    @FormParam("roles") roleNames: List<String>?,
  ): Response = httpResponseMetrics.timed("rest.admin.user-set-roles") {
    val roles = roleNames
      ?.mapNotNull { runCatching { UserRole.valueOf(it) }.getOrNull() }
      ?.toSet()
      ?: emptySet()
    val callingUsername = securityIdentity.principal.name
    adminUserManagement.setRoles(username, roles, callingUsername).fold(
      ifLeft = { error -> Response.ok(ApiResult(false, errorMessage(error.code))).build() },
      ifRight = { Response.ok(ApiResult(true, adminMsg.adminRolesUpdatedMessage())).build() },
    )
  }

  @DELETE
  @Path("/{username}")
  @Produces(MediaType.APPLICATION_JSON)
  fun deleteUser(@PathParam("username") username: String): Response = httpResponseMetrics.timed("rest.admin.user-delete") {
    val callingUsername = securityIdentity.principal.name
    adminUserManagement.deleteUser(username, callingUsername).fold(
      ifLeft = { error -> Response.ok(ApiResult(false, errorMessage(error.code))).build() },
      ifRight = { Response.ok(ApiResult(true, adminMsg.adminUserDeleteQueuedMessage())).build() },
    )
  }

  /** Polled by the users page while a delete enqueued via [deleteUser] runs in the background. */
  @GET
  @Path("/{username}/status")
  @Produces(MediaType.APPLICATION_JSON)
  fun userStatus(@PathParam("username") username: String): Response = httpResponseMetrics.timed("rest.admin.user-status") {
    val stillExists = adminUserManagement.listUsers().any { it.username.value == username }
    Response.ok(UserStatusResponse(stillExists)).build()
  }

  private fun renderUsers(): Any {
    val users = adminUserManagement.listUsers()
    return usersTemplate
      .data("users", users)
      .data("allRoles", UserRole.entries)
  }

  private fun renderUsersTable(): Any {
    val users = adminUserManagement.listUsers()
    return usersTemplate.getFragment("users_table")
      .data("users", users)
      .data("allRoles", UserRole.entries)
  }

  private fun errorMessage(code: String): String = when (code) {
    UserAdminError.USER_NOT_FOUND.code -> adminMsg.adminUserNotFoundError()
    UserAdminError.USERNAME_ALREADY_EXISTS.code -> adminMsg.adminUsernameExistsError()
    UserAdminError.BLANK_INPUT.code -> adminMsg.adminAllFieldsRequiredError()
    UserAdminError.CANNOT_DEACTIVATE_SELF.code -> adminMsg.adminCannotDeactivateSelfError()
    UserAdminError.CANNOT_DELETE_SELF.code -> adminMsg.adminCannotDeleteSelfError()
    UserAdminError.CANNOT_REMOVE_OWN_ADMIN_ROLE.code -> adminMsg.adminCannotRemoveOwnAdminRoleError()
    UserAdminError.SINGLE_ADMIN_VIOLATION.code -> adminMsg.adminSingleAdminViolationError()
    "password-blank" -> adminMsg.adminPasswordBlankError()
    else -> msg.commonUnexpectedError()
  }
}
