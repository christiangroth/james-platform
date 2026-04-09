package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import kotlin.math.abs
import mu.KLogging
import org.bson.Document
import org.bson.types.ObjectId
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class MigratePlaybackStartTimesStarter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : Starter {

  override val id = "MigratePlaybackStartTimesStarter-v2"

  override fun execute() {
    val database = mongoClient.getDatabase(databaseName)
    migrateCurrentlyPlayingStartTimes(database)
    migratePartialPlayedStartTimes(database)
    migrateRecentlyPlayedStartTimes(database)
    deleteSupersededPartialPlays(database)
  }

  private fun migrateCurrentlyPlayingStartTimes(database: MongoDatabase) {
    val result = database.getCollection(CURRENTLY_PLAYING_COLLECTION)
      .updateMany(
        Filters.not(Filters.exists(START_TIME_FIELD)),
        listOf(
          Document(
            "\$set",
            Document(START_TIME_FIELD, Document("\$subtract", listOf("\$$CP_OBSERVED_AT_FIELD", "\$$CP_PROGRESS_MS_FIELD"))),
          ),
        ),
      )
    logger.info { "Migrated startTime for ${result.modifiedCount} currently playing documents" }
  }

  private fun migratePartialPlayedStartTimes(database: MongoDatabase) {
    val result = database.getCollection(PARTIAL_PLAYED_COLLECTION)
      .updateMany(
        Filters.not(Filters.exists(START_TIME_FIELD)),
        listOf(
          Document(
            "\$set",
            Document(
              START_TIME_FIELD,
              Document(
                "\$subtract",
                listOf(
                  "\$$RPP_PLAYED_AT_FIELD",
                  Document("\$multiply", listOf("\$$RPP_PLAYED_SECONDS_FIELD", MS_PER_SECOND)),
                ),
              ),
            ),
          ),
        ),
      )
    logger.info { "Migrated startTime for ${result.modifiedCount} recently partial played documents" }
  }

  private fun migrateRecentlyPlayedStartTimes(database: MongoDatabase) {
    val result = database.getCollection(RECENTLY_PLAYED_COLLECTION)
      .updateMany(
        Filters.and(
          Filters.not(Filters.exists(START_TIME_FIELD)),
          Filters.gt(RP_DURATION_SECONDS_FIELD, 0L),
        ),
        listOf(
          Document(
            "\$set",
            Document(
              START_TIME_FIELD,
              Document(
                "\$subtract",
                listOf(
                  "\$$RP_PLAYED_AT_FIELD",
                  Document("\$multiply", listOf("\$$RP_DURATION_SECONDS_FIELD", MS_PER_SECOND)),
                ),
              ),
            ),
          ),
        ),
      )
    logger.info { "Migrated startTime for ${result.modifiedCount} recently played documents" }
  }

  private fun deleteSupersededPartialPlays(database: MongoDatabase) {
    val recentlyPlayedStartTimes = loadRecentlyPlayedStartTimes(database)
    if (recentlyPlayedStartTimes.isEmpty()) {
      logger.info { "No recently played documents with startTime found, skipping deduplication" }
      return
    }

    val (partialIdsToDelete, appPlaybackIdsToDelete) = findSupersededPartialIds(database, recentlyPlayedStartTimes)
    if (partialIdsToDelete.isNotEmpty()) {
      database.getCollection(PARTIAL_PLAYED_COLLECTION).deleteMany(Filters.`in`("_id", partialIdsToDelete))
      database.getCollection(APP_PLAYBACK_COLLECTION).deleteMany(Filters.`in`("_id", appPlaybackIdsToDelete))
      logger.info { "Deleted ${partialIdsToDelete.size} duplicate partial play(s) and ${appPlaybackIdsToDelete.size} app playback entry/entries" }
    } else {
      logger.info { "No duplicate partial plays found" }
    }
  }

  private fun loadRecentlyPlayedStartTimes(database: MongoDatabase): Map<Pair<String, String>, List<java.time.Instant>> {
    val result = mutableMapOf<Pair<String, String>, MutableList<java.time.Instant>>()
    database.getCollection(RECENTLY_PLAYED_COLLECTION).find(Filters.exists(START_TIME_FIELD)).forEach { doc ->
      val userId = doc.getString(SPOTIFY_USER_ID_FIELD)
      val trackId = doc.getString(TRACK_ID_FIELD)
      val startTime = doc.getDate(START_TIME_FIELD)?.toInstant()
      if (userId != null && trackId != null && startTime != null) {
        result.getOrPut(userId to trackId) { mutableListOf() }.add(startTime)
      }
    }
    return result
  }

  private fun findSupersededPartialIds(
    database: MongoDatabase,
    recentlyPlayedStartTimes: Map<Pair<String, String>, List<java.time.Instant>>,
  ): Pair<List<ObjectId>, List<String>> {
    val partialIds = mutableListOf<ObjectId>()
    val appPlaybackIds = mutableListOf<String>()
    database.getCollection(PARTIAL_PLAYED_COLLECTION).find(Filters.exists(START_TIME_FIELD)).forEach { doc ->
      val ids = resolveSupersededIds(doc, recentlyPlayedStartTimes)
      if (ids != null) {
        partialIds.add(ids.first)
        appPlaybackIds.add(ids.second)
      }
    }
    return partialIds to appPlaybackIds
  }

  private fun resolveSupersededIds(
    doc: Document,
    recentlyPlayedStartTimes: Map<Pair<String, String>, List<java.time.Instant>>,
  ): Pair<ObjectId, String>? {
    val fields = parsePartialDocFields(doc) ?: return null
    val recentlyPlayedForTrack = recentlyPlayedStartTimes[fields.userId to fields.trackId] ?: return null
    val isDuplicate = recentlyPlayedForTrack.any { rpStartTime ->
      abs(java.time.Duration.between(rpStartTime, fields.partialStartTime).toSeconds()) <= DEDUP_TOLERANCE_SECONDS
    }
    return if (isDuplicate) fields.docId to "${fields.userId}:${fields.playedAt.toEpochMilli()}" else null
  }

  private fun parsePartialDocFields(doc: Document): PartialDocFields? {
    val userId = doc.getString(SPOTIFY_USER_ID_FIELD)
    val trackId = doc.getString(TRACK_ID_FIELD)
    val partialStartTime = doc.getDate(START_TIME_FIELD)?.toInstant()
    if (userId == null || trackId == null || partialStartTime == null) return null
    val playedAt = doc.getDate(RPP_PLAYED_AT_FIELD)?.toInstant()
    val docId = doc.getObjectId("_id")
    if (playedAt == null || docId == null) return null
    return PartialDocFields(userId, trackId, partialStartTime, playedAt, docId)
  }

  private data class PartialDocFields(
    val userId: String,
    val trackId: String,
    val partialStartTime: java.time.Instant,
    val playedAt: java.time.Instant,
    val docId: ObjectId,
  )

  companion object : KLogging() {
    private const val CURRENTLY_PLAYING_COLLECTION = "spotify_currently_playing"
    private const val RECENTLY_PLAYED_COLLECTION = "spotify_recently_played"
    private const val PARTIAL_PLAYED_COLLECTION = "spotify_recently_partial_played"
    private const val APP_PLAYBACK_COLLECTION = "app_playback"
    private const val START_TIME_FIELD = "startTime"
    private const val SPOTIFY_USER_ID_FIELD = "spotifyUserId"
    private const val TRACK_ID_FIELD = "trackId"
    private const val RP_DURATION_SECONDS_FIELD = "durationSeconds"
    private const val RP_PLAYED_AT_FIELD = "playedAt"
    private const val CP_OBSERVED_AT_FIELD = "observedAt"
    private const val CP_PROGRESS_MS_FIELD = "progressMs"
    private const val RPP_PLAYED_AT_FIELD = "playedAt"
    private const val RPP_PLAYED_SECONDS_FIELD = "playedSeconds"
    private const val DEDUP_TOLERANCE_SECONDS = 8L
    private const val MS_PER_SECOND = 1000L
  }
}
