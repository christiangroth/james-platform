package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.BulkWriteOptions
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class AppArtistRepositoryAdapter(
  private val appArtistDocumentRepository: AppArtistDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : AppArtistRepositoryPort {

  override fun upsertAll(items: List<AppArtist>) {
    if (items.isEmpty()) return
    val collection = appArtistDocumentRepository.mongoCollection()
    val upsertOptions = UpdateOptions().upsert(true)
    val now = java.time.Instant.now()
    mongoQueryMetrics.timed("app_artist.upsertAll") {
      val requests = items.map { item ->
        UpdateOneModel<AppArtistDocument>(
          Filters.eq(ID_FIELD, item.id.value),
          Updates.combine(
            Updates.set(ARTIST_NAME_FIELD, item.artistName),
            Updates.set(IMAGE_LINK_FIELD, item.imageLink),
            Updates.set(TYPE_FIELD, item.type),
            Updates.set(LAST_SYNC_FIELD, now),
          ),
          upsertOptions,
        )
      }
      collection.bulkWrite(requests, BulkWriteOptions().ordered(false))
    }
  }

  override fun findAll(): List<AppArtist> =
    mongoQueryMetrics.timed("app_artist.findAll") {
      appArtistDocumentRepository.listAll().map { it.toDomain() }
    }

  override fun countAll(): Long =
    mongoQueryMetrics.timed("app_artist.countAll") {
      appArtistDocumentRepository.count()
    }

  override fun findByArtistIds(artistIds: Set<ArtistId>): List<AppArtist> {
    if (artistIds.isEmpty()) return emptyList()
    return mongoQueryMetrics.timed("app_artist.findByArtistIds") {
      appArtistDocumentRepository.mongoCollection()
        .find(Filters.`in`(ID_FIELD, artistIds.map { it.value }))
        .toList()
        .map { it.toDomain() }
    }
  }

  override fun findWithImageLinkAndBlankName(): List<AppArtist> =
    mongoQueryMetrics.timed("app_artist.findWithImageLinkAndBlankName") {
      appArtistDocumentRepository.mongoCollection()
        .find(
          Filters.and(
            Filters.ne("imageLink", null),
            Filters.or(
              Filters.eq("artistName", ""),
              Filters.exists("artistName", false),
            ),
          ),
        )
        .toList()
        .map { it.toDomain() }
    }

  override fun deleteAll() {
    logger.info { "Deleting all app_artist documents" }
    mongoQueryMetrics.timed("app_artist.deleteAll") {
      appArtistDocumentRepository.deleteAll()
    }
  }

  private fun AppArtistDocument.toDomain() = AppArtist(
    id = ArtistId(id),
    artistName = artistName,
    imageLink = imageLink,
    type = type,
    lastSync = lastSync?.toKotlinInstant() ?: kotlin.time.Instant.DISTANT_PAST,
  )

  companion object : KLogging() {
    internal const val ID_FIELD = "_id"
    internal const val ARTIST_NAME_FIELD = "artistName"
    internal const val IMAGE_LINK_FIELD = "imageLink"
    internal const val TYPE_FIELD = "type"
    internal const val LAST_SYNC_FIELD = "lastSync"
  }
}
