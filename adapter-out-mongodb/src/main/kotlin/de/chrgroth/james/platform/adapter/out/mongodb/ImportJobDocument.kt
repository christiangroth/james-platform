package de.chrgroth.james.platform.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "import_job")
class ImportJobDocument {

  @BsonId
  lateinit var id: String
  lateinit var userId: String
  lateinit var installedAppId: String
  lateinit var importDefinitionId: String
  lateinit var status: String
  lateinit var payload: String
  var detectedDataPaths: List<DataPathDocument> = emptyList()
  var detectedSchema: List<SchemaPropertyDocument> = emptyList()
  var filteredSchema: List<SchemaPropertyDocument> = emptyList()
  var triggeredBy: String = "USER"
  lateinit var createdAt: Instant
  lateinit var lastChangedAt: Instant
}

class DataPathDocument {
  lateinit var path: String
  var size: Int = 0
}

class SchemaPropertyDocument {
  lateinit var path: String
  var typeCounts: Map<String, Int> = emptyMap()
  var mandatory: Boolean = false
  var numericRange: NumericRangeDocument? = null
  var stringLengthCounts: Map<String, Int> = emptyMap()
}

class NumericRangeDocument {
  var min: Double = 0.0
  var max: Double = 0.0
}
