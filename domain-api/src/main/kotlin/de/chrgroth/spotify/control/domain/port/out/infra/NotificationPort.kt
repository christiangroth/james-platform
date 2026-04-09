package de.chrgroth.spotify.control.domain.port.out.infra

interface NotificationPort {
  fun notify(message: String)
}
