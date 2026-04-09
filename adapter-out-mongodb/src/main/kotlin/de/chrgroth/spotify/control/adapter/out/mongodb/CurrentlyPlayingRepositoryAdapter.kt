package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playback.CurrentlyPlayingRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class CurrentlyPlayingRepositoryAdapter(
  private val currentlyPlayingDocumentRepository: CurrentlyPlayingDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : CurrentlyPlayingRepositoryPort {

  override fun save(item: CurrentlyPlayingItem) {
    val document = CurrentlyPlayingDocument().apply {
      spotifyUserId = item.spotifyUserId.value
      trackId = item.trackId.value
      trackName = item.trackName
      artistIds = item.artistIds.map { it.value }
      artistNames = item.artistNames
      progressMs = item.progressMs
      durationMs = item.durationMs
      isPlaying = item.isPlaying
      observedAt = item.observedAt.toJavaInstant()
      startTime = item.startTime.toJavaInstant()
      albumId = item.albumId?.value
    }
    logger.info { "Saving currently playing document for user ${item.spotifyUserId.value}, track ${item.trackId.value}" }
    mongoQueryMetrics.timed("spotify_currently_playing.save") {
      currentlyPlayingDocumentRepository.persist(document)
    }
  }

  override fun findMostRecentByUserAndTrack(userId: UserId, trackId: TrackId): CurrentlyPlayingItem? =
    mongoQueryMetrics.timed("spotify_currently_playing.findMostRecentByUserAndTrack") {
      currentlyPlayingDocumentRepository
        .find("spotifyUserId = ?1 and trackId = ?2", userId.value, trackId.value)
        .list()
        .maxByOrNull { it.observedAt }
        ?.let { doc ->
          CurrentlyPlayingItem(
            spotifyUserId = UserId(doc.spotifyUserId),
            trackId = TrackId(doc.trackId),
            trackName = doc.trackName,
            artistIds = doc.artistIds.map { ArtistId(it) },
            artistNames = doc.artistNames,
            progressMs = doc.progressMs,
            durationMs = doc.durationMs,
            isPlaying = doc.isPlaying,
            observedAt = doc.observedAt.toKotlinInstant(),
            startTime = doc.startTime.toKotlinInstant(),
            albumId = doc.albumId?.let { AlbumId(it) },
          )
        }
    }

  override fun updateProgress(item: CurrentlyPlayingItem) {
    logger.info { "Updating currently playing progress for user ${item.spotifyUserId.value}, track ${item.trackId.value}, progressMs=${item.progressMs}" }
    mongoQueryMetrics.timed("spotify_currently_playing.updateProgress") {
      currentlyPlayingDocumentRepository.mongoCollection().findOneAndUpdate(
        Filters.and(
          Filters.eq(SPOTIFY_USER_ID_FIELD, item.spotifyUserId.value),
          Filters.eq(TRACK_ID_FIELD, item.trackId.value),
        ),
        Updates.combine(
          Updates.set(PROGRESS_MS_FIELD, item.progressMs),
          Updates.set(IS_PLAYING_FIELD, item.isPlaying),
          Updates.set(OBSERVED_AT_FIELD, item.observedAt.toJavaInstant()),
          Updates.set(START_TIME_FIELD, item.startTime.toJavaInstant()),
        ),
        FindOneAndUpdateOptions().sort(Sorts.descending(OBSERVED_AT_FIELD)),
      )
    }
  }

  override fun findByUserId(userId: UserId): List<CurrentlyPlayingItem> =
    mongoQueryMetrics.timed("spotify_currently_playing.findByUserId") {
      currentlyPlayingDocumentRepository
        .list("spotifyUserId = ?1", userId.value)
        .map { doc ->
          CurrentlyPlayingItem(
            spotifyUserId = UserId(doc.spotifyUserId),
            trackId = TrackId(doc.trackId),
            trackName = doc.trackName,
            artistIds = doc.artistIds.map { ArtistId(it) },
            artistNames = doc.artistNames,
            progressMs = doc.progressMs,
            durationMs = doc.durationMs,
            isPlaying = doc.isPlaying,
            observedAt = doc.observedAt.toKotlinInstant(),
            startTime = doc.startTime.toKotlinInstant(),
            albumId = doc.albumId?.let { AlbumId(it) },
          )
        }
    }

  override fun deleteByUserIdAndTrackIds(userId: UserId, trackIds: Set<String>) {
    if (trackIds.isEmpty()) return
    mongoQueryMetrics.timed("spotify_currently_playing.deleteByUserIdAndTrackIds") {
      currentlyPlayingDocumentRepository.delete(
        "spotifyUserId = ?1 and trackId in ?2",
        userId.value,
        trackIds.toList(),
      )
    }
  }

  companion object : KLogging() {
    internal const val SPOTIFY_USER_ID_FIELD = "spotifyUserId"
    internal const val TRACK_ID_FIELD = "trackId"
    internal const val OBSERVED_AT_FIELD = "observedAt"
    internal const val START_TIME_FIELD = "startTime"
    internal const val PROGRESS_MS_FIELD = "progressMs"
    internal const val IS_PLAYING_FIELD = "isPlaying"
  }
}
