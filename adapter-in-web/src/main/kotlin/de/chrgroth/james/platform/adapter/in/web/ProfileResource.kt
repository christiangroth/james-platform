package de.chrgroth.james.platform.adapter.`in`.web

import arrow.core.Either
import de.chrgroth.james.platform.adapter.`in`.web.i18n.AppMessages
import de.chrgroth.james.platform.domain.error.ApiTokenError
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.error.UserProfileError
import de.chrgroth.james.platform.domain.model.user.ApiToken
import de.chrgroth.james.platform.domain.model.user.ApiTokenId
import de.chrgroth.james.platform.domain.model.user.ApiTokenScope
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import de.chrgroth.james.platform.domain.port.`in`.user.ApiTokenPort
import de.chrgroth.james.platform.domain.port.`in`.user.UserProfileServicePort
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
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
import java.time.Instant

/** A personal access token as listed on the profile page. */
data class ApiTokenRow(
  val id: String,
  val name: String,
  val tokenPrefix: String,
  val scopes: List<String>,
  val appNames: List<String>,
  val active: Boolean,
  val createdAt: Instant,
  val lastUsedAt: Instant?,
)

/** A scope offered when creating a token. */
data class ApiTokenScopeOption(
  val name: String,
  val label: String,
  val description: String,
)

/** An installed App offered as App restriction for the User scopes of a new token. */
data class ApiTokenAppOption(
  val appId: String,
  val appName: String,
)

@Path("/ui/profile")
@ApplicationScoped
@Authenticated
@Suppress("Unused", "TooManyFunctions")
class ProfileResource {

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var userProfileService: UserProfileServicePort

  @Inject
  private lateinit var apiTokens: ApiTokenPort

  @Inject
  private lateinit var userAppStore: UserAppStorePort

  @Inject
  private lateinit var msg: AppMessages

  @Inject
  private lateinit var httpResponseMetrics: HttpResponseMetrics

  @GET
  @Produces(MediaType.TEXT_HTML)
  fun profile(): Any = httpResponseMetrics.timed("page.profile.view") { renderProfile() }

  @GET
  @Produces(MediaType.TEXT_HTML)
  @Path("/success")
  fun profileSuccess(@jakarta.ws.rs.QueryParam("msg") msg: String?): Any = httpResponseMetrics.timed("page.profile.success") {
    renderProfile(successMsg = msg?.let { successMessage(it) })
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  @Path("/error")
  fun profileError(@jakarta.ws.rs.QueryParam("error") error: String?): Any = httpResponseMetrics.timed("page.profile.error") {
    renderProfile(errorMsg = error?.let { errorMessage(it) })
  }

  @POST
  @Path("/username")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  fun changeUsername(@FormParam("newUsername") newUsername: String?): Response = httpResponseMetrics.timed("rest.profile.change-username") {
    val currentUsername = securityIdentity.principal.name
    if (newUsername.isNullOrBlank()) {
      return@timed Response.seeOther(URI.create("/ui/profile/error?error=${UserProfileError.BLANK_INPUT.code}")).build()
    }
    userProfileService.changeUsername(currentUsername, newUsername).fold(
      ifLeft = { error -> Response.seeOther(URI.create("/ui/profile/error?error=${error.code}")).build() },
      ifRight = { Response.seeOther(URI.create("/ui/profile/success?msg=username-changed")).build() },
    )
  }

  @POST
  @Path("/password")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  fun changePassword(
    @FormParam("currentPassword") currentPassword: String?,
    @FormParam("newPassword") newPassword: String?,
    @FormParam("confirmPassword") confirmPassword: String?,
  ): Response = httpResponseMetrics.timed("rest.profile.change-password") {
    val username = securityIdentity.principal.name
    if (currentPassword.isNullOrBlank() || newPassword.isNullOrBlank() || confirmPassword.isNullOrBlank()) {
      return@timed Response.seeOther(URI.create("/ui/profile/error?error=${UserProfileError.BLANK_INPUT.code}")).build()
    }
    if (newPassword != confirmPassword) {
      return@timed Response.seeOther(URI.create("/ui/profile/error?error=${UserProfileError.PASSWORDS_DO_NOT_MATCH.code}")).build()
    }
    userProfileService.changePassword(username, currentPassword, newPassword).fold(
      ifLeft = { error -> Response.seeOther(URI.create("/ui/profile/error?error=${error.code}")).build() },
      ifRight = { Response.seeOther(URI.create("/ui/profile/success?msg=password-changed")).build() },
    )
  }

  /**
   * Creates a personal access token. Answers with the rendered profile page (no redirect) so the plain token, shown exactly once, never ends up
   * in a URL or the browser history.
   */
  @POST
  @Path("/api-tokens")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.TEXT_HTML)
  fun createApiToken(
    @FormParam("name") name: String?,
    @FormParam("scopes") scopes: List<String>?,
    @FormParam("appIds") appIds: List<String>?,
  ): Any = httpResponseMetrics.timed("rest.profile.api-token-create") {
    val parsedScopes = scopes.orEmpty().mapNotNull { scope -> ApiTokenScope.entries.firstOrNull { it.name == scope } }.toSet()
    apiTokens.createToken(securityIdentity.principal.name, name.orEmpty(), parsedScopes, appIds.orEmpty().toSet()).fold(
      ifLeft = { error -> Response.seeOther(URI.create("/ui/profile/error?error=${error.code}")).build() },
      ifRight = { created -> renderProfile(successMsg = msg.profileApiTokenCreatedMessage(), createdToken = created.plainToken) },
    )
  }

  @POST
  @Path("/api-tokens/{id}/activate")
  fun activateApiToken(@PathParam("id") id: String): Response = httpResponseMetrics.timed("rest.profile.api-token-activate") {
    apiTokens.setActive(securityIdentity.principal.name, ApiTokenId(id), true).toRedirect("api-token-activated")
  }

  @POST
  @Path("/api-tokens/{id}/deactivate")
  fun deactivateApiToken(@PathParam("id") id: String): Response = httpResponseMetrics.timed("rest.profile.api-token-deactivate") {
    apiTokens.setActive(securityIdentity.principal.name, ApiTokenId(id), false).toRedirect("api-token-deactivated")
  }

  @POST
  @Path("/api-tokens/{id}/delete")
  fun deleteApiToken(@PathParam("id") id: String): Response = httpResponseMetrics.timed("rest.profile.api-token-delete") {
    apiTokens.deleteToken(securityIdentity.principal.name, ApiTokenId(id)).toRedirect("api-token-deleted")
  }

  private fun Either<DomainError, Unit>.toRedirect(successCode: String): Response = fold(
    ifLeft = { error -> Response.seeOther(URI.create("/ui/profile/error?error=${error.code}")).build() },
    ifRight = { Response.seeOther(URI.create("/ui/profile/success?msg=$successCode")).build() },
  )

  private fun successMessage(code: String): String = when (code) {
    "username-changed" -> msg.profileUsernameChangedMessage()
    "password-changed" -> msg.profilePasswordChangedMessage()
    "api-token-activated" -> msg.profileApiTokenActivatedMessage()
    "api-token-deactivated" -> msg.profileApiTokenDeactivatedMessage()
    "api-token-deleted" -> msg.profileApiTokenDeletedMessage()
    else -> msg.profileOperationCompletedMessage()
  }

  private fun errorMessage(code: String): String = when (code) {
    UserProfileError.USER_NOT_FOUND.code -> msg.profileUserNotFoundError()
    UserProfileError.USERNAME_ALREADY_EXISTS.code -> msg.profileUsernameExistsError()
    UserProfileError.INVALID_CURRENT_PASSWORD.code -> msg.profileInvalidCurrentPasswordError()
    UserProfileError.BLANK_INPUT.code -> msg.profileAllFieldsRequiredError()
    UserProfileError.PASSWORDS_DO_NOT_MATCH.code -> msg.profilePasswordsDoNotMatchError()
    ApiTokenError.NAME_BLANK.code -> msg.profileApiTokenNameRequiredError()
    ApiTokenError.NO_SCOPES.code -> msg.profileApiTokenScopeRequiredError()
    ApiTokenError.SCOPE_NOT_PERMITTED.code -> msg.profileApiTokenScopeNotPermittedError()
    ApiTokenError.TOKEN_NOT_FOUND.code -> msg.profileApiTokenNotFoundError()
    ApiTokenError.USER_NOT_FOUND.code -> msg.profileUserNotFoundError()
    else -> msg.commonUnexpectedError()
  }

  private fun renderProfile(successMsg: String? = null, errorMsg: String? = null, createdToken: String? = null): Any {
    val username = securityIdentity.principal.name
    return userProfileService.getProfile(username).fold(
      ifLeft = {
        UiTemplates.profile(username, null, null, successMsg, errorMsg, emptyList(), emptyList(), emptyList(), null)
      },
      ifRight = { user ->
        val appOptions = appOptions(user)
        UiTemplates.profile(
          username = user.username.value,
          createdAt = user.createdAt,
          lastLoginAt = user.lastLoginAt,
          successMessage = successMsg,
          errorMessage = errorMsg,
          apiTokens = apiTokens.listTokens(username).getOrNull().orEmpty().map { toRow(it, appOptions) },
          apiTokenScopes = ApiTokenScope.entries.filter { it.requiredRole in user.roles }.map { ApiTokenScopeOption(it.name, scopeLabel(it), scopeDescription(it)) },
          apiTokenApps = appOptions,
          createdApiToken = createdToken,
        )
      },
    )
  }

  private fun appOptions(user: User): List<ApiTokenAppOption> =
    userAppStore.getInstalledApps(user.username.value)
      .filter { !it.installedApp.isTest }
      .map { ApiTokenAppOption(it.installedApp.appId.value, it.appName) }
      .distinctBy { it.appId }
      .sortedBy { it.appName.lowercase() }

  private fun scopeLabel(scope: ApiTokenScope): String = when (scope) {
    ApiTokenScope.USER_READ -> msg.profileApiTokenScopeUserRead()
    ApiTokenScope.USER_WRITE -> msg.profileApiTokenScopeUserWrite()
    ApiTokenScope.DEVELOPER_READ -> msg.profileApiTokenScopeDeveloperRead()
    ApiTokenScope.DEVELOPER_WRITE -> msg.profileApiTokenScopeDeveloperWrite()
    ApiTokenScope.DEVELOPER_RELEASE -> msg.profileApiTokenScopeDeveloperRelease()
  }

  private fun scopeDescription(scope: ApiTokenScope): String = when (scope) {
    ApiTokenScope.USER_READ -> msg.profileApiTokenScopeUserReadDescription()
    ApiTokenScope.USER_WRITE -> msg.profileApiTokenScopeUserWriteDescription()
    ApiTokenScope.DEVELOPER_READ -> msg.profileApiTokenScopeDeveloperReadDescription()
    ApiTokenScope.DEVELOPER_WRITE -> msg.profileApiTokenScopeDeveloperWriteDescription()
    ApiTokenScope.DEVELOPER_RELEASE -> msg.profileApiTokenScopeDeveloperReleaseDescription()
  }

  private fun toRow(token: ApiToken, appOptions: List<ApiTokenAppOption>) = ApiTokenRow(
    id = token.id.value,
    name = token.name,
    tokenPrefix = token.tokenPrefix,
    scopes = token.scopes.sortedBy { it.ordinal }.map { scopeLabel(it) },
    // Apps uninstalled since the token was created are still shown by ID so the restriction stays visible
    appNames = token.appIds.map { appId -> appOptions.firstOrNull { it.appId == appId }?.appName ?: appId }.sorted(),
    active = token.active,
    createdAt = token.createdAt,
    lastUsedAt = token.lastUsedAt,
  )
}
