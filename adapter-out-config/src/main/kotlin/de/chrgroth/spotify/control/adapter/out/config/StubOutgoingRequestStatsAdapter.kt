package de.chrgroth.spotify.control.adapter.out.config

import de.chrgroth.spotify.control.domain.model.infra.OutgoingRequestStats
import de.chrgroth.spotify.control.domain.port.out.infra.OutgoingRequestStatsPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class StubOutgoingRequestStatsAdapter : OutgoingRequestStatsPort {

  override fun getRequestStats(): List<OutgoingRequestStats> = emptyList()
}
