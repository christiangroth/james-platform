package de.chrgroth.spotify.control.domain.port.`in`.user

import de.chrgroth.spotify.control.domain.model.user.RuntimeConfig

interface RuntimeConfigPort {
  fun getRuntimeConfig(): RuntimeConfig
  fun setThrottleIntervalSeconds(seconds: Long)
}
