package de.chrgroth.james.platform.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_user")
class UserDocument {

  @BsonId
  lateinit var id: String
  lateinit var username: String
  lateinit var passwordHash: String
  lateinit var roles: Set<String>
  lateinit var createdAt: Instant
  var lastLoginAt: Instant? = null
  var active: Boolean = true
}
