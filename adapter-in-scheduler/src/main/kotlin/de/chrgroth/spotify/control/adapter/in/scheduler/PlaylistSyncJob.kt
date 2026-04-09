package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistPort
import de.chrgroth.quarkus.starters.domain.ScheduledSkipPredicate
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class PlaylistSyncJob(
  private val playlist: PlaylistPort,
) {

  @Scheduled(cron = "0 30 * * * ?", skipExecutionIf = ScheduledSkipPredicate::class)
  fun run() {
    logger.info { "Running scheduled playlist sync" }
    playlist.enqueueUpdates()
  }

  companion object : KLogging()
}
