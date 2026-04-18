package de.chrgroth.james.platform.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "installed_app")
class InstalledAppDocument {

  @BsonId
  lateinit var id: String
  lateinit var userId: String
  lateinit var appId: String
  lateinit var installedVersionId: String
  lateinit var installedAt: Instant
}
