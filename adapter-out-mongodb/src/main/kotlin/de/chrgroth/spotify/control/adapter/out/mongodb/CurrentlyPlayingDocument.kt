package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.Instant

@MongoEntity(collection = "spotify_currently_playing")
class CurrentlyPlayingDocument {

  @BsonId
  var id: ObjectId = ObjectId()
  lateinit var spotifyUserId: String
  lateinit var trackId: String
  lateinit var trackName: String
  lateinit var artistIds: List<String>
  lateinit var artistNames: List<String>
  var progressMs: Long = 0L
  var durationMs: Long = 0L
  var isPlaying: Boolean = false
  lateinit var observedAt: Instant
  lateinit var startTime: Instant
  var albumId: String? = null
}
