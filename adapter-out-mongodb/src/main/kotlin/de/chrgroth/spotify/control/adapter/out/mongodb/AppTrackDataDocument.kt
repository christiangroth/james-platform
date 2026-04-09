package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_track")
class AppTrackDocument {

  @BsonId
  lateinit var id: String  // Set to trackId value; maps to MongoDB _id
  lateinit var title: String
  var albumId: String? = null
  var albumName: String? = null
  lateinit var artistId: String
  var artistName: String? = null
  var additionalArtistIds: List<String> = emptyList()
  var additionalArtistNames: List<String>? = null
  var discNumber: Int? = null
  var durationMs: Long? = null
  var trackNumber: Int? = null
  var type: String? = null
  var lastSync: Instant? = null
}
