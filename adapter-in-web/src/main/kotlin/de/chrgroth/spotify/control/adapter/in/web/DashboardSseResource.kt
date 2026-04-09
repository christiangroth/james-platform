package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.user.UserId
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import io.smallrye.mutiny.Multi
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.jboss.resteasy.reactive.RestStreamElementType

@Path("/dashboard/events")
@ApplicationScoped
@Suppress("Unused")
class DashboardSseResource(
  private val sseAdapter: DashboardSseAdapter,
) {

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @GET
  @Authenticated
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @RestStreamElementType(MediaType.TEXT_PLAIN)
  fun events(): Multi<String> = sseAdapter.stream(UserId(securityIdentity.principal.name))
}
