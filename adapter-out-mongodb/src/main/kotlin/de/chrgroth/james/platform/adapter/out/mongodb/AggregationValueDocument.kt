package de.chrgroth.james.platform.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_aggregation_view")
class AggregationValueDocument {

  @BsonId
  lateinit var id: String
  lateinit var installedAppId: String
  lateinit var aggregationDefinitionId: String
  var groupKey: String? = null
  var bucketKey: String? = null
  var value: Double = 0.0
  lateinit var status: String
  lateinit var updatedAt: Instant
}
