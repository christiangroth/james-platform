package de.chrgroth.spotify.control.domain.port.out.infra

interface OutboxTaskCountObserver {
  fun onOutboxTaskCountChanged()
}
