package de.chrgroth.james.platform.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app")
class AppDocument {

  @BsonId
  lateinit var id: String
  lateinit var name: String
  var description: String? = null
  lateinit var status: String
  lateinit var createdAt: Instant
  lateinit var updatedAt: Instant
}
