package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.error.UserAdminError
import de.chrgroth.james.platform.domain.port.`in`.user.AdminUserManagementPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.security.identity.SecurityIdentity
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.FormParam
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.net.URI

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
  @Path("/success")
  @Produces(MediaType.TEXT_HTML)
  fun usersSuccess(@jakarta.ws.rs.QueryParam("msg") msg: String?): Any =
    renderUsers(successMsg = msg?.let { successMessage(it) })

  @GET
  @Path("/error")
  @Produces(MediaType.TEXT_HTML)
  fun usersError(@jakarta.ws.rs.QueryParam("error") error: String?): Any =
    renderUsers(errorMsg = error?.let { errorMessage(it) })

  @POST
  @Path("/create")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  fun createUser(
    @FormParam("username") username: String?,
    @FormParam("password") password: String?,
  ): Response {
    if (username.isNullOrBlank() || password.isNullOrBlank()) {
      return Response.temporaryRedirect(URI.create("/ui/admin/users/error?error=${UserAdminError.BLANK_INPUT.code}")).build()
    }
    val callingUsername = securityIdentity.principal.name
    return adminUserManagement.createUser(username, password, callingUsername).fold(
      ifLeft = { error -> Response.temporaryRedirect(URI.create("/ui/admin/users/error?error=${error.code}")).build() },
      ifRight = { Response.temporaryRedirect(URI.create("/ui/admin/users/success?msg=user-created")).build() },
    )
  }

  @POST
  @Path("/{username}/activate")
  fun activateUser(@PathParam("username") username: String): Response =
    adminUserManagement.activateUser(username).fold(
      ifLeft = { error -> Response.temporaryRedirect(URI.create("/ui/admin/users/error?error=${error.code}")).build() },
      ifRight = { Response.temporaryRedirect(URI.create("/ui/admin/users/success?msg=user-activated")).build() },
    )

  @POST
  @Path("/{username}/deactivate")
  fun deactivateUser(@PathParam("username") username: String): Response {
    val callingUsername = securityIdentity.principal.name
    return adminUserManagement.deactivateUser(username, callingUsername).fold(
      ifLeft = { error -> Response.temporaryRedirect(URI.create("/ui/admin/users/error?error=${error.code}")).build() },
      ifRight = { Response.temporaryRedirect(URI.create("/ui/admin/users/success?msg=user-deactivated")).build() },
    )
  }

  @POST
  @Path("/{username}/password")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  fun setPassword(
    @PathParam("username") username: String,
    @FormParam("newPassword") newPassword: String?,
  ): Response {
    if (newPassword.isNullOrBlank()) {
      return Response.temporaryRedirect(URI.create("/ui/admin/users/error?error=password-blank")).build()
    }
    return adminUserManagement.setPassword(username, newPassword).fold(
      ifLeft = { error -> Response.temporaryRedirect(URI.create("/ui/admin/users/error?error=${error.code}")).build() },
      ifRight = { Response.temporaryRedirect(URI.create("/ui/admin/users/success?msg=password-set")).build() },
    )
  }

  @POST
  @Path("/{username}/delete")
  fun deleteUser(@PathParam("username") username: String): Response {
    val callingUsername = securityIdentity.principal.name
    return adminUserManagement.deleteUser(username, callingUsername).fold(
      ifLeft = { error -> Response.temporaryRedirect(URI.create("/ui/admin/users/error?error=${error.code}")).build() },
      ifRight = { Response.temporaryRedirect(URI.create("/ui/admin/users/success?msg=user-deleted")).build() },
    )
  }

  private fun renderUsers(successMsg: String? = null, errorMsg: String? = null): Any {
    val users = adminUserManagement.listUsers()
    return usersTemplate
      .data("users", users)
      .data("successMessage", successMsg)
      .data("errorMessage", errorMsg)
  }

  private fun successMessage(code: String): String = when (code) {
    "user-created" -> "User created successfully."
    "user-activated" -> "User activated successfully."
    "user-deactivated" -> "User deactivated successfully."
    "password-set" -> "Password set successfully."
    "user-deleted" -> "User deleted successfully."
    else -> "Operation completed successfully."
  }

  private fun errorMessage(code: String): String = when (code) {
    UserAdminError.USER_NOT_FOUND.code -> "User not found."
    UserAdminError.USERNAME_ALREADY_EXISTS.code -> "Username already exists. Please choose a different username."
    UserAdminError.BLANK_INPUT.code -> "All fields are required."
    UserAdminError.CANNOT_DEACTIVATE_SELF.code -> "You cannot deactivate your own account."
    UserAdminError.CANNOT_DELETE_SELF.code -> "You cannot delete your own account."
    "password-blank" -> "Password must not be empty."
    else -> "An unexpected error occurred. Please try again."
  }
}
