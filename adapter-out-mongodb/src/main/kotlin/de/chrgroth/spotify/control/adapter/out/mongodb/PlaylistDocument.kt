package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId

@MongoEntity(collection = "spotify_playlist")
class PlaylistDocument {

  @BsonId
  lateinit var id: String
  lateinit var spotifyUserId: String
  lateinit var spotifyPlaylistId: String
  lateinit var tracks: List<PlaylistTrackSubdocument>
}

class PlaylistTrackSubdocument {
  lateinit var trackId: String
  lateinit var artistIds: List<String>
  var albumId: String? = null
}
