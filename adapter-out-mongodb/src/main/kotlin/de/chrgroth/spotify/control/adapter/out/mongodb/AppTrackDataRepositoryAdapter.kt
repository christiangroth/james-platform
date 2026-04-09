package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.BulkWriteOptions
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class AppTrackRepositoryAdapter(
  private val appTrackDocumentRepository: AppTrackDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : AppTrackRepositoryPort {

  override fun upsertAll(items: List<AppTrack>) {
    if (items.isEmpty()) return
    val collection = appTrackDocumentRepository.mongoCollection()
    val upsertOptions = UpdateOptions().upsert(true)
    val now = java.time.Instant.now()
    mongoQueryMetrics.timed("app_track.upsertAll") {
      val requests = items.map { item ->
        UpdateOneModel<AppTrackDocument>(
          Filters.eq("_id", item.id.value),
          Updates.combine(
            Updates.set("title", item.title),
            Updates.set("albumId", item.albumId?.value),
            Updates.set("albumName", item.albumName),
            Updates.set("artistId", item.artistId.value),
            Updates.set("artistName", item.artistName),
            Updates.set("additionalArtistIds", item.additionalArtistIds.map { it.value }),
            Updates.set("additionalArtistNames", item.additionalArtistNames),
            Updates.set("discNumber", item.discNumber),
            Updates.set("durationMs", item.durationMs),
            Updates.set("trackNumber", item.trackNumber),
            Updates.set("type", item.type),
            Updates.set("lastSync", now),
          ),
          upsertOptions,
        )
      }
      collection.bulkWrite(requests, BulkWriteOptions().ordered(false))
    }
  }

  override fun findAll(): List<AppTrack> =
    mongoQueryMetrics.timed("app_track.findAll") {
      appTrackDocumentRepository.listAll().map { it.toDomain() }
    }

  override fun countAll(): Long =
    mongoQueryMetrics.timed("app_track.countAll") {
      appTrackDocumentRepository.count()
    }

  override fun findByTrackIds(trackIds: Set<TrackId>): List<AppTrack> {
    if (trackIds.isEmpty()) return emptyList()
    return mongoQueryMetrics.timed("app_track.findByTrackIds") {
      appTrackDocumentRepository.mongoCollection()
        .find(Filters.`in`("_id", trackIds.map { it.value }))
        .toList()
        .map { it.toDomain() }
    }
  }

  override fun findByArtistId(artistId: ArtistId): List<AppTrack> =
    mongoQueryMetrics.timed("app_track.findByArtistId") {
      appTrackDocumentRepository.list("artistId = ?1", artistId.value).map { it.toDomain() }
    }

  override fun findByAlbumId(albumId: AlbumId): List<AppTrack> =
    mongoQueryMetrics.timed("app_track.findByAlbumId") {
      appTrackDocumentRepository.list("albumId = ?1", albumId.value).map { it.toDomain() }
    }

  override fun deleteAll() {
    logger.info { "Deleting all app_track documents" }
    mongoQueryMetrics.timed("app_track.deleteAll") {
      appTrackDocumentRepository.deleteAll()
    }
  }

  private fun AppTrackDocument.toDomain() = AppTrack(
    id = TrackId(id),
    title = title,
    albumId = albumId?.let { AlbumId(it) },
    albumName = albumName,
    artistId = ArtistId(artistId),
    artistName = artistName,
    additionalArtistIds = additionalArtistIds.map { ArtistId(it) },
    additionalArtistNames = additionalArtistNames,
    discNumber = discNumber,
    durationMs = durationMs,
    trackNumber = trackNumber,
    type = type,
    lastSync = lastSync?.toKotlinInstant() ?: kotlin.time.Instant.DISTANT_PAST,
  )

  companion object : KLogging() {
    internal const val ID_FIELD = "_id"
    internal const val ARTIST_ID_FIELD = "artistId"
    internal const val ALBUM_ID_FIELD = "albumId"
  }
}
