package de.chrgroth.spotify.control.domain.model.user

data class RuntimeConfig(
  val throttleIntervalSeconds: Long,
  val defaultThrottleIntervalSeconds: Long,
)
