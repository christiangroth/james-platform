package de.chrgroth.james.platform.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId

@MongoEntity(collection = "app_playback_aggregation")
class PlaybackAggregationDocument {

  /**
   * Composite key: "${spotifyUserId}:${type}:${periodStart}"
   */
  @BsonId
  lateinit var id: String
  lateinit var spotifyUserId: String
  lateinit var type: String
  lateinit var periodStart: String
  var totalPlaybackSeconds: Long = 0L
  var distinctArtistCount: Int = 0
  var distinctTrackCount: Int = 0
  var artistEntries: List<PlaybackAggregationEntryDocument> = emptyList()
  var trackEntries: List<PlaybackAggregationEntryDocument> = emptyList()
  var activityEntries: List<PlaybackAggregationActivityEntryDocument> = emptyList()
}

class PlaybackAggregationEntryDocument {
  lateinit var id: String
  lateinit var name: String
  var totalSeconds: Long = 0L
}

class PlaybackAggregationActivityEntryDocument {
  lateinit var dayOfWeek: String
  lateinit var timeWindow: String
  var totalSeconds: Long = 0L
}
