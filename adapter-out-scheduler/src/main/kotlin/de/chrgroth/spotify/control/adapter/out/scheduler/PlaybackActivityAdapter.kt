package de.chrgroth.spotify.control.adapter.out.scheduler

import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackActivityPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackDetectedObserver
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@ApplicationScoped
class PlaybackActivityAdapter : PlaybackActivityPort, PlaybackDetectedObserver {

  private val lastPlaybackDetectedAtRef = AtomicReference<Instant>(Instant.fromEpochMilliseconds(0))

  override fun onPlaybackDetected() {
    lastPlaybackDetectedAtRef.set(Clock.System.now())
  }

  override fun isPlaybackActive(): Boolean =
    (Clock.System.now() - lastPlaybackDetectedAtRef.get()) < PLAYBACK_ACTIVE_THRESHOLD

  override fun lastActivityTimestamp(): Instant? {
    val ts = lastPlaybackDetectedAtRef.get()
    return if (ts == Instant.fromEpochMilliseconds(0)) null else ts
  }

  companion object {
    private val PLAYBACK_ACTIVE_THRESHOLD = 5.minutes
  }
}
