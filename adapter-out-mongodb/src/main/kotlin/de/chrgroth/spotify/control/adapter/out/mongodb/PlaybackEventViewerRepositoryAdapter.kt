package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.MongoException
import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import de.chrgroth.spotify.control.domain.model.playback.RawPlaybackEvent
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackEventViewerRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging
import org.bson.BsonDateTime
import org.bson.BsonDocument
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class PlaybackEventViewerRepositoryAdapter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : PlaybackEventViewerRepositoryPort {

  private val jsonWriterSettings = JsonWriterSettings.builder()
    .indent(true)
    .outputMode(JsonMode.RELAXED)
    .build()

  override fun findRecentlyPlayed(userId: UserId, from: Instant, to: Instant): List<RawPlaybackEvent> =
    queryCollection(RECENTLY_PLAYED_COLLECTION, PLAYED_AT_FIELD, userId, from, to)

  override fun findRecentlyPartialPlayed(userId: UserId, from: Instant, to: Instant): List<RawPlaybackEvent> =
    queryCollection(RECENTLY_PARTIAL_PLAYED_COLLECTION, PLAYED_AT_FIELD, userId, from, to)

  override fun findCurrentlyPlaying(userId: UserId, from: Instant, to: Instant): List<RawPlaybackEvent> =
    queryCollection(CURRENTLY_PLAYING_COLLECTION, OBSERVED_AT_FIELD, userId, from, to)

  private fun queryCollection(
    collectionName: String,
    timestampField: String,
    userId: UserId,
    from: Instant,
    to: Instant,
  ): List<RawPlaybackEvent> {
    val coll = mongoClient.getDatabase(databaseName).getCollection(collectionName, BsonDocument::class.java)
    return try {
      val filter = Filters.and(
        Filters.eq(SPOTIFY_USER_ID_FIELD, userId.value),
        Filters.gte(timestampField, from.toJavaInstant()),
        Filters.lt(timestampField, to.toJavaInstant()),
      )
      coll.find(filter).sort(Sorts.ascending(timestampField)).map { doc ->
        val bsonTs = doc[timestampField]
        val ts = if (bsonTs is BsonDateTime) {
          java.time.Instant.ofEpochMilli(bsonTs.value).toKotlinInstant()
        } else {
          Instant.DISTANT_PAST
        }
        RawPlaybackEvent(timestamp = ts, json = doc.toJson(jsonWriterSettings))
      }.toList()
    } catch (e: MongoException) {
      logger.warn(e) { "Failed to query $collectionName for playback event viewer" }
      emptyList()
    }
  }

  companion object : KLogging() {
    private const val RECENTLY_PLAYED_COLLECTION = "spotify_recently_played"
    private const val RECENTLY_PARTIAL_PLAYED_COLLECTION = "spotify_recently_partial_played"
    private const val CURRENTLY_PLAYING_COLLECTION = "spotify_currently_playing"
    private const val SPOTIFY_USER_ID_FIELD = "spotifyUserId"
    private const val PLAYED_AT_FIELD = "playedAt"
    private const val OBSERVED_AT_FIELD = "observedAt"
  }
}
