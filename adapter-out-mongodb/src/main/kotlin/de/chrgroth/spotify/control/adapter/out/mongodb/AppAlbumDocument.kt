package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_album")
class AppAlbumDocument {

  @BsonId
  lateinit var id: String  // Set to albumId value; maps to MongoDB _id
  var totalTracks: Int? = null
  var title: String? = null
  var imageLink: String? = null
  var releaseDate: String? = null
  var releaseDatePrecision: String? = null
  var type: String? = null
  var artistId: String? = null
  var artistName: String? = null
  var additionalArtistIds: List<String>? = null
  var additionalArtistNames: List<String>? = null
  var lastSync: Instant? = null
}
