package de.chrgroth.james.platform.domain.port.out.infra

interface OutboxTaskCountObserver {
  fun onOutboxTaskCountChanged()
}
