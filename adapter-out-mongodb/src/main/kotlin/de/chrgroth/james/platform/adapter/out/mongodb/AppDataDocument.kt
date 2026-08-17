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

  // Defaults to empty rather than lateinit: documents saved before this field existed lack it, and the startup backfill Starter
  // reads them (via AppDataRepositoryAdapter.toDomain()) before it can set a real value.
  var lastValidatedWithVersion: String = ""
  lateinit var entityType: String
  var objectVersion: Int = 1
  lateinit var createdAt: Instant
  lateinit var lastChangedAt: Instant
  var data: Map<String, String?> = emptyMap()
}
