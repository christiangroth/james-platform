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
  var detectedDataPaths: List<DataPathDocument> = emptyList()
  var selectedDataPath: String? = null
  var detectedSchema: List<SchemaPropertyDocument> = emptyList()
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
