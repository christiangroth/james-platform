package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackPort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class PlaybackDetectionJob(
  private val playback: PlaybackPort,
) {

  @Scheduled(every = "20s", skipExecutionIf = CurrentlyPlayingSkipPredicate::class)
  fun run() {
    logger.info { "Running scheduled playback detection" }
    playback.enqueueFetchPlaybackData()
  }

  companion object : KLogging()
}
