package de.chrgroth.spotify.control.adapter.`in`.scheduler

/*
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogPort
import de.chrgroth.quarkus.starters.domain.ScheduledSkipPredicate
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.LocalDate
import java.time.ZoneOffset

@ApplicationScoped
@Suppress("Unused")
class ArtistCatalogSyncJob(
  private val catalog: CatalogPort,
) {

  @Scheduled(cron = "0 0 2 * * ?", skipExecutionIf = ScheduledSkipPredicate::class)
  fun run() {
    val partition = LocalDate.now(ZoneOffset.UTC).dayOfYear % TOTAL_PARTITIONS
    logger.info { "Running scheduled artist catalog sync for partition $partition/$TOTAL_PARTITIONS" }
    catalog.enqueueArtistAlbumsSync(partition, TOTAL_PARTITIONS)
  }

  companion object : KLogging() {
    private const val TOTAL_PARTITIONS = 14
  }
}
*/
