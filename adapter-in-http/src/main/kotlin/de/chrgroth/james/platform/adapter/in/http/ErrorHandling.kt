package de.chrgroth.james.platform.adapter.`in`.http

import arrow.core.NonEmptyList
import de.chrgroth.james.DomainError
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

internal class CustomStatusException(
  val status: Response.Status,
) : RuntimeException()

@Provider
@Suppress("Unused")
internal class CustomStatusExceptionMapper : ExceptionMapper<CustomStatusException> {

  override fun toResponse(e: CustomStatusException): Response =
    Response.status(e.status).build()
}

internal class DomainErrorsException(
  val errors: NonEmptyList<DomainError>,
) : RuntimeException()

@Provider
@Suppress("Unused")
internal class DomainErrorsExceptionMapper : ExceptionMapper<DomainErrorsException> {

  override fun toResponse(e: DomainErrorsException): Response {
    // TODO also log domain errors??
    // TODO map to correct status code depending on contained errors
    // TODO add response entity and define in Open API
    return Response.serverError().build()
  }
}
