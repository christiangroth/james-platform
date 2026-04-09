package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "spotify_playlist_metadata")
class PlaylistMetadataDocument {

  @BsonId
  lateinit var id: String
  lateinit var spotifyUserId: String
  lateinit var spotifyPlaylistId: String
  lateinit var snapshotId: String
  lateinit var lastSnapshotIdSyncTime: Instant
  lateinit var name: String
  lateinit var syncStatus: String
  var type: String? = null
  var lastSyncTime: java.time.Instant? = null
}
