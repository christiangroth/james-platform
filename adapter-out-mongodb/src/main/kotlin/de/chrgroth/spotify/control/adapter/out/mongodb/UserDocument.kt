package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_user")
class UserDocument {

  @BsonId
  lateinit var spotifyUserId: String
  lateinit var displayName: String
  lateinit var encryptedAccessToken: String
  lateinit var encryptedRefreshToken: String
  lateinit var tokenExpiresAt: Instant
  lateinit var createdAt: Instant
  lateinit var lastLoginAt: Instant
}
