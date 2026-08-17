package de.chrgroth.james.platform.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_data")
class AppDataDocument {

  @BsonId
  lateinit var id: String
  lateinit var userId: String
  lateinit var installedAppId: String
  lateinit var appVersion: String
  lateinit var entityType: String
  var objectVersion: Int = 1
  lateinit var createdAt: Instant
  lateinit var lastChangedAt: Instant
  var data: Map<String, String?> = emptyMap()
}
