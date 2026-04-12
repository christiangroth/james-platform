package de.chrgroth.james.platform.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId

@MongoEntity(collection = "app_encryption_key")
class EncryptionKeyDocument {

  @BsonId
  lateinit var id: String
  lateinit var key: String
}
