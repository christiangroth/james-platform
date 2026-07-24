package de.chrgroth.james.platform.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "import_document")
class ImportDocumentDocument {

  @BsonId
  lateinit var id: String
  lateinit var userId: String
  lateinit var installedAppId: String
  lateinit var sourceUrl: String
  lateinit var encryptedBearerToken: String
  lateinit var status: String
  lateinit var payload: String
  lateinit var createdAt: Instant
  lateinit var lastChangedAt: Instant
}
