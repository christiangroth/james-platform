package de.chrgroth.james.platform.domain.port.out.infra

interface NotificationPort {
  fun notify(message: String)
}
