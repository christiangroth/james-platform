package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.playback.aggregation.ActivityEntry
import de.chrgroth.spotify.control.domain.model.playback.aggregation.ActivityTimeWindow
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationRankEntry
import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackAggregationRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import java.time.DayOfWeek
import kotlinx.datetime.LocalDate
import mu.KLogging

@ApplicationScoped
class PlaybackAggregationRepositoryAdapter(
  private val repository: PlaybackAggregationDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : PlaybackAggregationRepositoryPort {

  override fun save(aggregation: PlaybackAggregation) {
    val doc = aggregation.toDocument()
    logger.info { "Saving playback aggregation: ${doc.id}" }
    mongoQueryMetrics.timed("app_playback_aggregation.save") {
      repository.persistOrUpdate(doc)
    }
  }

  override fun deleteAll() {
    val count = mongoQueryMetrics.timed("app_playback_aggregation.deleteAll") {
      repository.deleteAll()
    }
    logger.info { "Deleted $count playback aggregation document(s)" }
  }

  override fun findByUserAndPeriod(userId: UserId, type: AggregationPeriodType, periodStart: LocalDate): PlaybackAggregation? {
    val id = documentId(userId, type, periodStart)
    return mongoQueryMetrics.timed("app_playback_aggregation.findByUserAndPeriod") {
      repository.findById(id)?.toDomain()
    }
  }

  override fun findByUserTypeAndPeriodRange(userId: UserId, type: AggregationPeriodType, from: LocalDate, to: LocalDate): List<PlaybackAggregation> =
    mongoQueryMetrics.timed("app_playback_aggregation.findByUserTypeAndPeriodRange") {
      repository.list(
        "$SPOTIFY_USER_ID_FIELD = ?1 and $TYPE_FIELD = ?2 and $PERIOD_START_FIELD >= ?3 and $PERIOD_START_FIELD <= ?4",
        userId.value,
        type.name,
        from.toString(),
        to.toString(),
      ).map { it.toDomain() }
    }

  private fun PlaybackAggregation.toDocument(): PlaybackAggregationDocument = PlaybackAggregationDocument().apply {
    id = documentId(this@toDocument.userId, this@toDocument.type, this@toDocument.periodStart)
    spotifyUserId = this@toDocument.userId.value
    type = this@toDocument.type.name
    periodStart = this@toDocument.periodStart.toString()
    totalPlaybackSeconds = this@toDocument.totalPlaybackSeconds
    distinctArtistCount = this@toDocument.distinctArtistCount
    distinctTrackCount = this@toDocument.distinctTrackCount
    artistEntries = this@toDocument.artistEntries.map { it.toEntryDocument() }
    trackEntries = this@toDocument.trackEntries.map { it.toEntryDocument() }
    activityEntries = this@toDocument.activityEntries.map { it.toActivityDocument() }
  }

  private fun AggregationRankEntry.toEntryDocument(): PlaybackAggregationEntryDocument = PlaybackAggregationEntryDocument().apply {
    id = this@toEntryDocument.id
    name = this@toEntryDocument.name
    totalSeconds = this@toEntryDocument.totalSeconds
  }

  private fun ActivityEntry.toActivityDocument(): PlaybackAggregationActivityEntryDocument = PlaybackAggregationActivityEntryDocument().apply {
    dayOfWeek = this@toActivityDocument.dayOfWeek.name
    timeWindow = this@toActivityDocument.timeWindow.name
    totalSeconds = this@toActivityDocument.totalSeconds
  }

  private fun PlaybackAggregationDocument.toDomain(): PlaybackAggregation = PlaybackAggregation(
    userId = UserId(spotifyUserId),
    type = AggregationPeriodType.valueOf(type),
    periodStart = LocalDate.parse(periodStart),
    totalPlaybackSeconds = totalPlaybackSeconds,
    distinctArtistCount = distinctArtistCount,
    distinctTrackCount = distinctTrackCount,
    artistEntries = artistEntries.map { it.toDomain() },
    trackEntries = trackEntries.map { it.toDomain() },
    activityEntries = activityEntries.map { it.toDomain() },
  )

  private fun PlaybackAggregationEntryDocument.toDomain(): AggregationRankEntry = AggregationRankEntry(
    id = id,
    name = name,
    totalSeconds = totalSeconds,
  )

  private fun PlaybackAggregationActivityEntryDocument.toDomain(): ActivityEntry = ActivityEntry(
    dayOfWeek = DayOfWeek.valueOf(dayOfWeek),
    timeWindow = ActivityTimeWindow.valueOf(timeWindow),
    totalSeconds = totalSeconds,
  )

  companion object : KLogging() {
    internal const val SPOTIFY_USER_ID_FIELD = "spotifyUserId"
    internal const val TYPE_FIELD = "type"
    internal const val PERIOD_START_FIELD = "periodStart"

    internal fun documentId(userId: UserId, type: AggregationPeriodType, periodStart: LocalDate): String =
      "${userId.value}:${type.name}:$periodStart"
  }
}
