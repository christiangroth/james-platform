package de.chrgroth.spotify.control.adapter.out.scheduler

import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackDetectedObserver
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackStatePort
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
