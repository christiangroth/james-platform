package de.chrgroth.spotify.control.domain.port.out.infra

interface OutgoingRequestStatsObserver {
  fun onRequestRecorded()
}
