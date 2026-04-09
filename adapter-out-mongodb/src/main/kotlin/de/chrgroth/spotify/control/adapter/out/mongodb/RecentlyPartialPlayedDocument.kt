package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.Instant

@MongoEntity(collection = "spotify_recently_partial_played")
class RecentlyPartialPlayedDocument {

  @BsonId
  var id: ObjectId = ObjectId()
  lateinit var spotifyUserId: String
  lateinit var trackId: String
  lateinit var trackName: String
  lateinit var artistIds: List<String>
  lateinit var artistNames: List<String>
  lateinit var playedAt: Instant
  lateinit var startTime: Instant
  var playedSeconds: Long = 0L
  var albumId: String? = null
}
