package de.chrgroth.james.platform.adapter.`in`.web

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

  @GET
  @Produces(MediaType.TEXT_HTML)
  fun users(): Any = renderUsers()

  @GET
  @Path("/table")
  @Produces(MediaType.TEXT_HTML)
  fun usersTable(): Any = renderUsersTable()

  @PUT
  @Path("/{username}")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun createUser(
    @PathParam("username") username: String,
    @FormParam("password") password: String?,
  ): Response {
    if (password.isNullOrBlank()) {
      return Response.ok(ApiResult(false, errorMessage(UserAdminError.BLANK_INPUT.code))).build()
    }
    val callingUsername = securityIdentity.principal.name
    return adminUserManagement.createUser(username, password, callingUsername).fold(
      ifLeft = { error -> Response.ok(ApiResult(false, errorMessage(error.code))).build() },
      ifRight = { Response.ok(ApiResult(true, "User created successfully.")).build() },
    )
  }

  @POST
  @Path("/{username}/activation")
  @Produces(MediaType.APPLICATION_JSON)
  fun activateUser(@PathParam("username") username: String): Response =
    adminUserManagement.activateUser(username).fold(
      ifLeft = { error -> Response.ok(ApiResult(false, errorMessage(error.code))).build() },
      ifRight = { Response.ok(ApiResult(true, "User activated successfully.")).build() },
    )

  @DELETE
  @Path("/{username}/activation")
  @Produces(MediaType.APPLICATION_JSON)
  fun deactivateUser(@PathParam("username") username: String): Response {
    val callingUsername = securityIdentity.principal.name
    return adminUserManagement.deactivateUser(username, callingUsername).fold(
      ifLeft = { error -> Response.ok(ApiResult(false, errorMessage(error.code))).build() },
      ifRight = { Response.ok(ApiResult(true, "User deactivated successfully.")).build() },
    )
  }

  @POST
  @Path("/{username}/password")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun setPassword(
    @PathParam("username") username: String,
    @FormParam("newPassword") newPassword: String?,
  ): Response {
    if (newPassword.isNullOrBlank()) {
      return Response.ok(ApiResult(false, errorMessage("password-blank"))).build()
    }
    return adminUserManagement.setPassword(username, newPassword).fold(
      ifLeft = { error -> Response.ok(ApiResult(false, errorMessage(error.code))).build() },
      ifRight = { Response.ok(ApiResult(true, "Password set successfully.")).build() },
    )
  }

  @POST
  @Path("/{username}/roles")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun setRoles(
    @PathParam("username") username: String,
    @FormParam("roles") roleNames: List<String>?,
  ): Response {
    val roles = roleNames
      ?.mapNotNull { runCatching { UserRole.valueOf(it) }.getOrNull() }
      ?.toSet()
      ?: emptySet()
    val callingUsername = securityIdentity.principal.name
    return adminUserManagement.setRoles(username, roles, callingUsername).fold(
      ifLeft = { error -> Response.ok(ApiResult(false, errorMessage(error.code))).build() },
      ifRight = { Response.ok(ApiResult(true, "Roles updated successfully.")).build() },
    )
  }

  @DELETE
  @Path("/{username}")
  @Produces(MediaType.APPLICATION_JSON)
  fun deleteUser(@PathParam("username") username: String): Response {
    val callingUsername = securityIdentity.principal.name
    return adminUserManagement.deleteUser(username, callingUsername).fold(
      ifLeft = { error -> Response.ok(ApiResult(false, errorMessage(error.code))).build() },
      ifRight = { Response.ok(ApiResult(true, "User deleted successfully.")).build() },
    )
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
    UserAdminError.USER_NOT_FOUND.code -> "User not found."
    UserAdminError.USERNAME_ALREADY_EXISTS.code -> "Username already exists. Please choose a different username."
    UserAdminError.BLANK_INPUT.code -> "All fields are required."
    UserAdminError.CANNOT_DEACTIVATE_SELF.code -> "You cannot deactivate your own account."
    UserAdminError.CANNOT_DELETE_SELF.code -> "You cannot delete your own account."
    UserAdminError.CANNOT_REMOVE_OWN_ADMIN_ROLE.code -> "You cannot remove your own admin role."
    UserAdminError.SINGLE_ADMIN_VIOLATION.code -> "Another user already has the admin role. Only one admin is allowed at a time."
    "password-blank" -> "Password must not be empty."
    else -> "An unexpected error occurred. Please try again."
  }
}
