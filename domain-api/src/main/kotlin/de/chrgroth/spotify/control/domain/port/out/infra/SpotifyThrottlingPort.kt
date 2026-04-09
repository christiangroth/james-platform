package de.chrgroth.spotify.control.domain.port.out.infra

interface SpotifyThrottlingPort {
  fun getDefaultThrottleIntervalMs(): Long
  fun getThrottleIntervalMs(): Long
  fun setThrottleIntervalMs(ms: Long)
}
