package de.chrgroth.james.platform.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "import_connection")
class ImportConnectionDocument {

  @BsonId
  lateinit var id: String
  lateinit var userId: String
  lateinit var name: String
  lateinit var baseUrl: String
  var encryptedBearerToken: String? = null
  lateinit var createdAt: Instant
  lateinit var lastChangedAt: Instant
}
