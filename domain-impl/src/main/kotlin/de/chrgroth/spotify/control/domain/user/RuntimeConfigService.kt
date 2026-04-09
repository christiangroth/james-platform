package de.chrgroth.spotify.control.domain.user

import de.chrgroth.spotify.control.domain.model.user.RuntimeConfig
import de.chrgroth.spotify.control.domain.port.`in`.user.RuntimeConfigPort
import de.chrgroth.spotify.control.domain.port.out.infra.SpotifyThrottlingPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class RuntimeConfigService(
  private val spotifyThrottling: SpotifyThrottlingPort,
) : RuntimeConfigPort {

  override fun getRuntimeConfig(): RuntimeConfig = RuntimeConfig(
    throttleIntervalSeconds = spotifyThrottling.getThrottleIntervalMs() / MILLIS_PER_SECOND,
    defaultThrottleIntervalSeconds = spotifyThrottling.getDefaultThrottleIntervalMs() / MILLIS_PER_SECOND,
  )

  override fun setThrottleIntervalSeconds(seconds: Long) {
    spotifyThrottling.setThrottleIntervalMs(seconds * MILLIS_PER_SECOND)
  }

  companion object {
    private const val MILLIS_PER_SECOND = 1000L
  }
}
