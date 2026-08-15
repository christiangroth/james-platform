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

  /** Named `url` for backward compatibility with already-persisted documents; holds the connection's base URL - see [de.chrgroth.james.platform.domain.model.imports.ImportConnection.baseUrl]. */
  lateinit var url: String
  var encryptedBearerToken: String? = null
  lateinit var createdAt: Instant
  lateinit var lastChangedAt: Instant
}
