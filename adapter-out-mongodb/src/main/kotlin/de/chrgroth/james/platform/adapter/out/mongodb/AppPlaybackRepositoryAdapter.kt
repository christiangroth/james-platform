package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.client.model.Accumulators
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import de.chrgroth.james.platform.domain.model.playback.AppPlaybackItem
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.port.out.playback.AppPlaybackRepositoryPort
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import kotlinx.datetime.LocalDate
import mu.KLogging
import org.bson.Document

@ApplicationScoped
class AppPlaybackRepositoryAdapter(
  private val appPlaybackDocumentRepository: AppPlaybackDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : AppPlaybackRepositoryPort {

  override fun saveAll(items: List<AppPlaybackItem>) {
    if (items.isEmpty()) return
    val documents = items.map { item ->
      AppPlaybackDocument().apply {
        id = "${item.userId.value}:${item.playedAt.toEpochMilliseconds()}"
        spotifyUserId = item.userId.value
        playedAt = item.playedAt.toJavaInstant()
        trackId = item.trackId
        secondsPlayed = item.secondsPlayed
      }
    }
    logger.info { "Saving ${documents.size} app_playback documents" }
    mongoQueryMetrics.timed("app_playback.saveAll") {
      appPlaybackDocumentRepository.persistOrUpdate(documents)
    }
  }

  override fun deleteAllByUserId(userId: UserId) {
    logger.info { "Deleting all app_playback documents for user: ${userId.value}" }
    mongoQueryMetrics.timed("app_playback.deleteAllByUserId") {
      appPlaybackDocumentRepository.delete("spotifyUserId = ?1", userId.value)
    }
  }

  override fun deleteAllByTrackIds(trackIds: Set<String>) {
    if (trackIds.isEmpty()) return
    logger.info { "Deleting all app_playback documents for ${trackIds.size} track(s)" }
    mongoQueryMetrics.timed("app_playback.deleteAllByTrackIds") {
      appPlaybackDocumentRepository.delete("trackId in ?1", trackIds.toList())
    }
  }

  override fun deleteByUserAndPlayedAts(userId: UserId, playedAts: Set<Instant>) {
    if (playedAts.isEmpty()) return
    val javaPlayedAts = playedAts.map { it.toJavaInstant() }
    logger.info { "Deleting ${playedAts.size} app_playback document(s) for user: ${userId.value}" }
    mongoQueryMetrics.timed("app_playback.deleteByUserAndPlayedAts") {
      appPlaybackDocumentRepository.delete("spotifyUserId = ?1 and playedAt in ?2", userId.value, javaPlayedAts)
    }
  }

  override fun findMostRecentPlayedAt(userId: UserId): Instant? =
    mongoQueryMetrics.timed("app_playback.findMostRecentPlayedAt") {
      appPlaybackDocumentRepository
        .find("spotifyUserId = ?1", Sort.by("playedAt").descending(), userId.value)
        .firstResult()
        ?.playedAt?.toKotlinInstant()
    }

  override fun findOldestPlayedAt(): Instant? =
    mongoQueryMetrics.timed("app_playback.findOldestPlayedAt") {
      appPlaybackDocumentRepository
        .findAll(Sort.by("playedAt").ascending())
        .firstResult()
        ?.playedAt?.toKotlinInstant()
    }
  override fun findExistingPlayedAts(userId: UserId, playedAts: Set<Instant>): Set<Instant> {
    if (playedAts.isEmpty()) return emptySet()
    val javaPlayedAts = playedAts.map { it.toJavaInstant() }
    return mongoQueryMetrics.timed("app_playback.findExistingPlayedAts") {
      appPlaybackDocumentRepository
        .list("spotifyUserId = ?1 and playedAt in ?2", userId.value, javaPlayedAts)
        .map { it.playedAt.toKotlinInstant() }
        .toSet()
    }
  }

  override fun countAll(userId: UserId): Long =
    mongoQueryMetrics.timed("app_playback.countAll") {
      appPlaybackDocumentRepository.count("spotifyUserId = ?1", userId.value)
    }

  override fun countSince(userId: UserId, since: Instant): Long =
    mongoQueryMetrics.timed("app_playback.countSince") {
      appPlaybackDocumentRepository.count(
        "spotifyUserId = ?1 and playedAt >= ?2",
        userId.value,
        since.toJavaInstant(),
      )
    }

  override fun countPerDaySince(userId: UserId, since: Instant): List<Pair<LocalDate, Long>> {
    val pipeline = listOf(
      Aggregates.match(
        Filters.and(
          Filters.eq(SPOTIFY_USER_ID_FIELD, userId.value),
          Filters.gte(PLAYED_AT_FIELD, since.toJavaInstant()),
        ),
      ),
      Aggregates.group(
        Document("\$dateToString", Document("format", "%Y-%m-%d").append("date", "\$$PLAYED_AT_FIELD")),
        Accumulators.sum("count", 1),
      ),
      Aggregates.sort(Sorts.ascending("_id")),
    )
    return mongoQueryMetrics.timed("app_playback.countPerDaySince") {
      appPlaybackDocumentRepository.mongoCollection()
        .aggregate(pipeline, Document::class.java)
        .map { doc ->
          val dateStr = doc.getString("_id")
          val count = (doc["count"] as? Int)?.toLong() ?: doc.getLong("count") ?: 0L
          LocalDate.parse(dateStr) to count
        }
        .toList()
    }
  }

  override fun findRecentlyPlayed(userId: UserId, limit: Int): List<AppPlaybackItem> =
    mongoQueryMetrics.timed("app_playback.findRecentlyPlayed") {
      appPlaybackDocumentRepository
        .find("spotifyUserId = ?1", Sort.by("playedAt").descending(), userId.value)
        .page(0, limit)
        .list()
        .map { doc ->
          AppPlaybackItem(
            userId = UserId(doc.spotifyUserId),
            playedAt = doc.playedAt.toKotlinInstant(),
            trackId = doc.trackId,
            secondsPlayed = doc.secondsPlayed,
          )
        }
    }

  override fun findAllSince(userId: UserId, since: Instant): List<AppPlaybackItem> =
    mongoQueryMetrics.timed("app_playback.findAllSince") {
      appPlaybackDocumentRepository
        .list("spotifyUserId = ?1 and playedAt >= ?2", userId.value, since.toJavaInstant())
        .map { doc ->
          AppPlaybackItem(
            userId = UserId(doc.spotifyUserId),
            playedAt = doc.playedAt.toKotlinInstant(),
            trackId = doc.trackId,
            secondsPlayed = doc.secondsPlayed,
          )
        }
    }

  override fun findAllBetween(userId: UserId, from: Instant, to: Instant): List<AppPlaybackItem> =
    mongoQueryMetrics.timed("app_playback.findAllBetween") {
      appPlaybackDocumentRepository
        .list("spotifyUserId = ?1 and playedAt >= ?2 and playedAt < ?3", userId.value, from.toJavaInstant(), to.toJavaInstant())
        .map { doc ->
          AppPlaybackItem(
            userId = UserId(doc.spotifyUserId),
            playedAt = doc.playedAt.toKotlinInstant(),
            trackId = doc.trackId,
            secondsPlayed = doc.secondsPlayed,
          )
        }
    }

  override fun sumSecondsPlayedByTrackIdSince(userId: UserId, since: Instant): Map<String, Long> {
    val pipeline = listOf(
      Aggregates.match(
        Filters.and(
          Filters.eq(SPOTIFY_USER_ID_FIELD, userId.value),
          Filters.gte(PLAYED_AT_FIELD, since.toJavaInstant()),
          Filters.gt(SECONDS_PLAYED_FIELD, 0),
        ),
      ),
      Aggregates.group("\$$TRACK_ID_FIELD", Accumulators.sum("totalSeconds", "\$$SECONDS_PLAYED_FIELD")),
    )
    return mongoQueryMetrics.timed("app_playback.sumSecondsPlayedByTrackIdSince") {
      appPlaybackDocumentRepository.mongoCollection()
        .aggregate(pipeline, Document::class.java)
        .associate { doc ->
          val trackId = doc.getString("_id")
          val totalSeconds = (doc["totalSeconds"] as? Int)?.toLong() ?: doc.getLong("totalSeconds") ?: 0L
          trackId to totalSeconds
        }
    }
  }

  override fun findAllDistinctTrackIds(): Set<String> =
    mongoQueryMetrics.timed("app_playback.findAllDistinctTrackIds") {
      appPlaybackDocumentRepository.mongoCollection()
        .distinct(TRACK_ID_FIELD, String::class.java)
        .toList()
        .toSet()
    }

  companion object : KLogging() {
    internal const val SPOTIFY_USER_ID_FIELD = "spotifyUserId"
    internal const val PLAYED_AT_FIELD = "playedAt"
    internal const val TRACK_ID_FIELD = "trackId"
    internal const val SECONDS_PLAYED_FIELD = "secondsPlayed"
  }
}
