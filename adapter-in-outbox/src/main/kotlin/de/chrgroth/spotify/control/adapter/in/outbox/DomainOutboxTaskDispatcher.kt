package de.chrgroth.spotify.control.adapter.`in`.outbox

/*
import arrow.core.Either
import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxDispatcher
import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxEvent
import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxPartition
import de.chrgroth.quarkus.outbox.domain.DispatchResult
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogPort
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackAggregationPort
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackPort
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistCheckPort
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistPort
import de.chrgroth.spotify.control.domain.port.`in`.user.UserProfilePort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import kotlin.time.Clock
import kotlin.time.toJavaInstant

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class DomainOutboxTaskDispatcher(
  private val playback: PlaybackPort,
  private val playbackAggregation: PlaybackAggregationPort,
  private val catalog: CatalogPort,
  private val playlist: PlaylistPort,
  private val playlistCheck: PlaylistCheckPort,
  private val userProfile: UserProfilePort,
) : ApplicationOutboxDispatcher {

  override fun getAllPartitions(): List<ApplicationOutboxPartition> = DomainOutboxPartition.all

  override fun deserialize(partition: ApplicationOutboxPartition, eventType: String, payload: String): ApplicationOutboxEvent =
    DomainOutboxEvent.fromKey(eventType, payload)

  override fun dispatch(event: ApplicationOutboxEvent): DispatchResult = dispatchEvent(event as DomainOutboxEvent)

  @Suppress("CyclomaticComplexMethod")
  private fun dispatchEvent(event: DomainOutboxEvent): DispatchResult =
    handleDomainOperation(event.key) {
      when (event) {
        is DomainOutboxEvent.FetchPlaybackData -> playback.handle(event)
        is DomainOutboxEvent.UpdateUserProfile -> userProfile.handle(event)
        is DomainOutboxEvent.SyncPlaylistInfo -> playlist.handle(event)
        is DomainOutboxEvent.SyncPlaylistData -> playlist.handle(event)
        is DomainOutboxEvent.RebuildPlaybackData -> playback.handle(event)
        is DomainOutboxEvent.AppendPlaybackData -> playback.handle(event)
        is DomainOutboxEvent.AggregatePlaybackData -> playbackAggregation.handle(event)
        is DomainOutboxEvent.SyncArtistDetails -> catalog.handle(event)
        is DomainOutboxEvent.SyncArtistAlbums -> catalog.handle(event)
        is DomainOutboxEvent.SyncAlbumDetails -> catalog.handle(event)
        is DomainOutboxEvent.ResyncCatalog -> catalog.handle(event)
        is DomainOutboxEvent.RunPlaylistChecks -> playlistCheck.handle(event)
      }
    }

  private fun handleDomainOperation(taskDescription: String, operation: () -> Either<DomainError, Unit>): DispatchResult = try {
    when (val result = operation()) {
      is Either.Right -> DispatchResult.Success
      is Either.Left -> when (val error = result.value) {
        is SpotifyRateLimitError -> {
          logger.warn { "Rate limited on $taskDescription, retry after ${error.retryAfter.inWholeSeconds}s" }
          DispatchResult.Paused("Rate limited: $taskDescription", (Clock.System.now() + error.retryAfter).toJavaInstant())
        }
        else -> {
          logger.error { "Failed $taskDescription: ${error.code}" }
          DispatchResult.Failed("Failed $taskDescription: ${error.code}")
        }
      }
    }
  } catch (e: Exception) {
    logger.error(e) { "Unexpected error in $taskDescription" }
    DispatchResult.Failed("Unexpected error in $taskDescription: ${e.message}", e)
  }

  companion object : KLogging()
}
*/
