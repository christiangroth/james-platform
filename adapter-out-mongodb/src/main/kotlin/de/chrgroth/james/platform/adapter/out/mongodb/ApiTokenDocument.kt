package de.chrgroth.james.platform.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "api_token")
class ApiTokenDocument {

  @BsonId
  lateinit var id: String
  lateinit var userId: String
  lateinit var name: String
  lateinit var tokenHash: String
  lateinit var tokenPrefix: String
  lateinit var scopes: Set<String>
  var appIds: Set<String> = emptySet()
  var active: Boolean = true
  lateinit var createdAt: Instant
  var lastUsedAt: Instant? = null
}
