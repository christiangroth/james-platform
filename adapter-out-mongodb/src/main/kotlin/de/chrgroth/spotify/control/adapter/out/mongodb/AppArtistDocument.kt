package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_artist")
class AppArtistDocument {

  @BsonId
  lateinit var id: String  // Set to artistId value; maps to MongoDB _id
  lateinit var artistName: String
  var imageLink: String? = null
  var type: String? = null
  var lastSync: Instant? = null
}
