package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.quarkus.starters.domain.ScheduledSkipPredicate
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackActivityPort
import io.quarkus.scheduler.Scheduled
import io.quarkus.scheduler.ScheduledExecution
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@ApplicationScoped
class CurrentlyPlayingSkipPredicate : Scheduled.SkipPredicate {

  private val playbackActivity: PlaybackActivityPort
  private val starterSkipPredicate: ScheduledSkipPredicate

  @Inject
  constructor(playbackActivity: PlaybackActivityPort) : this(playbackActivity, ScheduledSkipPredicate())

  internal constructor(
    playbackActivity: PlaybackActivityPort,
    starterSkipPredicate: ScheduledSkipPredicate,
  ) {
    this.playbackActivity = playbackActivity
    this.starterSkipPredicate = starterSkipPredicate
  }

  private val lastExecutedAtRef = AtomicReference<Instant>(Instant.fromEpochMilliseconds(0))

  override fun test(execution: ScheduledExecution): Boolean {
    if (starterSkipPredicate.test(execution)) return true
    val effectiveInterval = if (playbackActivity.isPlaybackActive()) FAST_INTERVAL else SLOW_INTERVAL
    val now = Clock.System.now()
    val lastExecutedAt = lastExecutedAtRef.get()
    if (now < lastExecutedAt + effectiveInterval) return true
    return !lastExecutedAtRef.compareAndSet(lastExecutedAt, now)
  }

  companion object {
    private val FAST_INTERVAL: Duration = 20.seconds
    private val SLOW_INTERVAL: Duration = 5.minutes
  }
}
