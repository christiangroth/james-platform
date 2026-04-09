package de.chrgroth.spotify.control.domain.port.out.infra

import de.chrgroth.spotify.control.domain.model.infra.OutgoingRequestStats

interface OutgoingRequestStatsPort {
  fun getRequestStats(): List<OutgoingRequestStats>
}
