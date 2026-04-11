package de.chrgroth.james.platform.adapter.out.scheduler

import de.chrgroth.james.platform.domain.port.out.playback.PlaybackDetectedObserver
import de.chrgroth.james.platform.domain.port.out.playback.PlaybackStatePort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance

@ApplicationScoped
class CurrentlyPlayingScheduleState(
  private val playbackDetectedObservers: Instance<PlaybackDetectedObserver>,
) : PlaybackStatePort {

  override fun onPlaybackDetected() {
    playbackDetectedObservers.forEach { it.onPlaybackDetected() }
  }
}
