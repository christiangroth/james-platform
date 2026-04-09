package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_playlist_check")
class AppPlaylistCheckDocument {

  @BsonId
  lateinit var checkId: String
  lateinit var playlistId: String
  lateinit var lastCheck: Instant
  var succeeded: Boolean = false
  lateinit var violations: List<String>
}
