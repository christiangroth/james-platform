package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.error.UserProfileError
import de.chrgroth.james.platform.domain.port.`in`.user.UserProfileServicePort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.FormParam
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.net.URI

@Path("/ui/profile")
@ApplicationScoped
@Authenticated
@Suppress("Unused")
class ProfileResource {

  @Inject
  @Location("ui/profile.html")
  private lateinit var profileTemplate: Template

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var userProfileService: UserProfileServicePort

  @GET
  @Produces(MediaType.TEXT_HTML)
  fun profile(): Any = renderProfile()

  @GET
  @Produces(MediaType.TEXT_HTML)
  @Path("/success")
  fun profileSuccess(@jakarta.ws.rs.QueryParam("msg") msg: String?): Any =
    renderProfile(successMsg = msg?.let { successMessage(it) })

  @GET
  @Produces(MediaType.TEXT_HTML)
  @Path("/error")
  fun profileError(@jakarta.ws.rs.QueryParam("error") error: String?): Any =
    renderProfile(errorMsg = error?.let { errorMessage(it) })

  @POST
  @Path("/username")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  fun changeUsername(@FormParam("newUsername") newUsername: String?): Response {
    val currentUsername = securityIdentity.principal.name
    if (newUsername.isNullOrBlank()) {
      return Response.temporaryRedirect(URI.create("/ui/profile/error?error=${UserProfileError.BLANK_INPUT.code}")).build()
    }
    return userProfileService.changeUsername(currentUsername, newUsername).fold(
      ifLeft = { error -> Response.temporaryRedirect(URI.create("/ui/profile/error?error=${error.code}")).build() },
      ifRight = { Response.temporaryRedirect(URI.create("/ui/profile/success?msg=username-changed")).build() },
    )
  }

  @POST
  @Path("/password")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  fun changePassword(
    @FormParam("currentPassword") currentPassword: String?,
    @FormParam("newPassword") newPassword: String?,
    @FormParam("confirmPassword") confirmPassword: String?,
  ): Response {
    val username = securityIdentity.principal.name
    if (currentPassword.isNullOrBlank() || newPassword.isNullOrBlank() || confirmPassword.isNullOrBlank()) {
      return Response.temporaryRedirect(URI.create("/ui/profile/error?error=${UserProfileError.BLANK_INPUT.code}")).build()
    }
    if (newPassword != confirmPassword) {
      return Response.temporaryRedirect(URI.create("/ui/profile/error?error=${UserProfileError.PASSWORDS_DO_NOT_MATCH.code}")).build()
    }
    return userProfileService.changePassword(username, currentPassword, newPassword).fold(
      ifLeft = { error -> Response.temporaryRedirect(URI.create("/ui/profile/error?error=${error.code}")).build() },
      ifRight = { Response.temporaryRedirect(URI.create("/ui/profile/success?msg=password-changed")).build() },
    )
  }

  private fun successMessage(code: String): String = when (code) {
    "username-changed" -> "Username changed successfully."
    "password-changed" -> "Password changed successfully."
    else -> "Operation completed successfully."
  }

  private fun errorMessage(code: String): String = when (code) {
    UserProfileError.USER_NOT_FOUND.code -> "User not found."
    UserProfileError.USERNAME_ALREADY_EXISTS.code -> "Username already exists. Please choose a different username."
    UserProfileError.INVALID_CURRENT_PASSWORD.code -> "Current password is incorrect."
    UserProfileError.BLANK_INPUT.code -> "All fields are required."
    UserProfileError.PASSWORDS_DO_NOT_MATCH.code -> "New passwords do not match."
    else -> "An unexpected error occurred. Please try again."
  }

  private fun renderProfile(successMsg: String? = null, errorMsg: String? = null): Any {
    val username = securityIdentity.principal.name
    return userProfileService.getProfile(username).fold(
      ifLeft = {
        profileTemplate
          .data("username", username)
          .data("createdAt", null)
          .data("lastLoginAt", null)
          .data("successMessage", successMsg)
          .data("errorMessage", errorMsg)
      },
      ifRight = { user ->
        profileTemplate
          .data("username", user.username.value)
          .data("createdAt", user.createdAt)
          .data("lastLoginAt", user.lastLoginAt)
          .data("successMessage", successMsg)
          .data("errorMessage", errorMsg)
      },
    )
  }
}
