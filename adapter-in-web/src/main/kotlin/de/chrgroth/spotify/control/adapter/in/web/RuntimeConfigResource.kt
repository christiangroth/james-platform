package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.port.`in`.user.RuntimeConfigPort
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import mu.KLogging

@Path("/settings/runtime-config")
@ApplicationScoped
@Suppress("Unused")
class RuntimeConfigResource {

  @Inject
  private lateinit var runtimeConfig: RuntimeConfigPort

  @POST
  @Authenticated
  @Path("/throttle-interval")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun updateThrottleInterval(request: ThrottleIntervalRequest): Response {
    if (request.intervalSeconds < 0) {
      return Response.status(Response.Status.BAD_REQUEST)
        .entity(mapOf("error" to "Throttle interval must be non-negative"))
        .build()
    }
    runtimeConfig.setThrottleIntervalSeconds(request.intervalSeconds)
    logger.info { "Runtime throttle interval updated to ${request.intervalSeconds}s" }
    return Response.ok(mapOf("status" to "ok", "intervalSeconds" to request.intervalSeconds)).build()
  }

  data class ThrottleIntervalRequest(val intervalSeconds: Long = 0)

  companion object : KLogging()
}
