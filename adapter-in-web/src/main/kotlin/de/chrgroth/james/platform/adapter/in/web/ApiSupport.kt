package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.error.AppDataConstraintViolationError
import de.chrgroth.james.platform.domain.error.DisplayTextInvalidError
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.error.InvalidAggregationDefinitionError
import de.chrgroth.james.platform.domain.error.InvalidMigrationStepError
import de.chrgroth.james.platform.domain.error.InvalidObjectStructureError
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

/** Error body of every `/api/v1` error response. [code] is a [DomainError.code] or a generic code such as `FORBIDDEN`. */
data class ApiErrorResponse(
  val code: String,
  val message: String,
  val details: List<String> = emptyList(),
)

object ApiResponses {

  fun ok(body: Any): Response = Response.ok(body).type(MediaType.APPLICATION_JSON).build()

  fun created(body: Any): Response = Response.status(Response.Status.CREATED).entity(body).type(MediaType.APPLICATION_JSON).build()

  fun noContent(): Response = Response.noContent().build()

  fun error(status: Response.Status, code: String, message: String, details: List<String> = emptyList()): Response =
    Response.status(status).entity(ApiErrorResponse(code, message, details)).type(MediaType.APPLICATION_JSON).build()

  fun notFound(message: String): Response = error(Response.Status.NOT_FOUND, "NOT_FOUND", message)

  fun forbidden(message: String): Response = error(Response.Status.FORBIDDEN, "FORBIDDEN", message)

  fun badRequest(message: String): Response = error(Response.Status.BAD_REQUEST, "BAD_REQUEST", message)

  /** Maps a [DomainError] to 404 for "not found" errors and 400 for everything else, carrying the error's code and name. */
  fun domainError(error: DomainError): Response {
    val name = (error as? Enum<*>)?.name ?: error::class.simpleName ?: "DomainError"
    val status = if (name.endsWith("NOT_FOUND")) Response.Status.NOT_FOUND else Response.Status.BAD_REQUEST
    return error(status, error.code, name, details(error))
  }

  private fun details(error: DomainError): List<String> = when (error) {
    is AppDataConstraintViolationError ->
      error.propertyViolations.flatMap { (propertyId, violations) -> violations.map { "$propertyId: ${it.code}" } } +
        error.pathedViolations.map { "${it.path}: ${it.violation.code}" }
    is InvalidObjectStructureError -> error.entityNames
    is InvalidAggregationDefinitionError -> error.entityNames
    is InvalidMigrationStepError -> error.entityNames
    is DisplayTextInvalidError -> error.entityNames
    else -> emptyList()
  }
}

/** Accessors for the personal access token request currently being handled (see [ApiTokenAuthMechanism]). */
@ApplicationScoped
@Suppress("Unused")
class ApiRequestContext(
  private val identity: SecurityIdentity,
) {

  /** The token owner's username, used as `userId` by the User-side ports. */
  fun username(): String = identity.principal.name

  /** The token owner's [de.chrgroth.james.platform.domain.model.user.UserId], used as `developerId` by the Developer-side ports. */
  fun userId(): String = identity.getAttribute(ApiTokenAuthMechanism.API_TOKEN_USER_ID_ATTRIBUTE)

  /** Whether the token's User scopes cover data of the App [appId] (no App restriction means all Apps). */
  fun coversApp(appId: String): Boolean {
    val appIds: Set<String>? = identity.getAttribute(ApiTokenAuthMechanism.API_TOKEN_APP_IDS_ATTRIBUTE)
    return appIds.isNullOrEmpty() || appId in appIds
  }
}
