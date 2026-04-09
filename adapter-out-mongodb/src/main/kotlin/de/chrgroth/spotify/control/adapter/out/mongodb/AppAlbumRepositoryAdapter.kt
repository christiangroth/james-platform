package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.BulkWriteOptions
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppAlbum
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class AppAlbumRepositoryAdapter(
  private val appAlbumDocumentRepository: AppAlbumDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : AppAlbumRepositoryPort {

  override fun upsertAll(items: List<AppAlbum>) {
    if (items.isEmpty()) return
    val collection = appAlbumDocumentRepository.mongoCollection()
    val upsertOptions = UpdateOptions().upsert(true)
    val now = java.time.Instant.now()
    mongoQueryMetrics.timed("app_album.upsertAll") {
      val requests = items.map { item ->
        UpdateOneModel<AppAlbumDocument>(
          Filters.eq(ID_FIELD, item.id.value),
          Updates.combine(
            Updates.set(TOTAL_TRACKS_FIELD, item.totalTracks),
            Updates.set(TITLE_FIELD, item.title),
            Updates.set(IMAGE_LINK_FIELD, item.imageLink),
            Updates.set(RELEASE_DATE_FIELD, item.releaseDate),
            Updates.set(RELEASE_DATE_PRECISION_FIELD, item.releaseDatePrecision),
            Updates.set(TYPE_FIELD, item.type),
            Updates.set(ARTIST_ID_FIELD, item.artistId?.value),
            Updates.set(ARTIST_NAME_FIELD, item.artistName),
            Updates.set(ADDITIONAL_ARTIST_IDS_FIELD, item.additionalArtistIds?.map { it.value }),
            Updates.set(ADDITIONAL_ARTIST_NAMES_FIELD, item.additionalArtistNames),
            Updates.set(LAST_SYNC_FIELD, now),
          ),
          upsertOptions,
        )
      }
      collection.bulkWrite(requests, BulkWriteOptions().ordered(false))
    }
  }

  override fun findAll(): List<AppAlbum> =
    mongoQueryMetrics.timed("app_album.findAll") {
      appAlbumDocumentRepository.listAll().map { it.toDomain() }
    }

  override fun countAll(): Long =
    mongoQueryMetrics.timed("app_album.countAll") {
      appAlbumDocumentRepository.count()
    }

  override fun findByAlbumIds(albumIds: Set<AlbumId>): List<AppAlbum> {
    if (albumIds.isEmpty()) return emptyList()
    return mongoQueryMetrics.timed("app_album.findByAlbumIds") {
      appAlbumDocumentRepository.mongoCollection()
        .find(Filters.`in`(ID_FIELD, albumIds.map { it.value }))
        .toList()
        .map { it.toDomain() }
    }
  }

  override fun findByArtistId(artistId: ArtistId): List<AppAlbum> =
    mongoQueryMetrics.timed("app_album.findByArtistId") {
      appAlbumDocumentRepository.list("artistId = ?1", artistId.value).map { it.toDomain() }
    }

  override fun deleteAll() {
    logger.info { "Deleting all app_album documents" }
    mongoQueryMetrics.timed("app_album.deleteAll") {
      appAlbumDocumentRepository.deleteAll()
    }
  }

  private fun AppAlbumDocument.toDomain() = AppAlbum(
    id = AlbumId(id),
    totalTracks = totalTracks,
    title = title,
    imageLink = imageLink,
    releaseDate = releaseDate,
    releaseDatePrecision = releaseDatePrecision,
    type = type,
    artistId = artistId?.let { ArtistId(it) },
    artistName = artistName,
    additionalArtistIds = additionalArtistIds?.map { ArtistId(it) },
    additionalArtistNames = additionalArtistNames,
    lastSync = lastSync?.toKotlinInstant() ?: kotlin.time.Instant.DISTANT_PAST,
  )

  companion object : KLogging() {
    internal const val ID_FIELD = "_id"
    internal const val TOTAL_TRACKS_FIELD = "totalTracks"
    internal const val TITLE_FIELD = "title"
    internal const val IMAGE_LINK_FIELD = "imageLink"
    internal const val RELEASE_DATE_FIELD = "releaseDate"
    internal const val RELEASE_DATE_PRECISION_FIELD = "releaseDatePrecision"
    internal const val TYPE_FIELD = "type"
    internal const val ARTIST_ID_FIELD = "artistId"
    internal const val ARTIST_NAME_FIELD = "artistName"
    internal const val ADDITIONAL_ARTIST_IDS_FIELD = "additionalArtistIds"
    internal const val ADDITIONAL_ARTIST_NAMES_FIELD = "additionalArtistNames"
    internal const val LAST_SYNC_FIELD = "lastSync"
  }
}
