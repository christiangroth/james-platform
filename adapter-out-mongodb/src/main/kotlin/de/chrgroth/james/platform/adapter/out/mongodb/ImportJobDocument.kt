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
  lateinit var connectionId: String
  var urlPostfix: String? = null
  lateinit var targetEntityDefinitionId: String
  lateinit var status: String
  lateinit var payload: String
  var detectedDataPaths: List<DataPathDocument> = emptyList()
  var selectedDataPath: String? = null
  var detectedSchema: List<SchemaPropertyDocument> = emptyList()
  var filteredSchema: List<SchemaPropertyDocument> = emptyList()
  var filterRules: List<FilterRuleDocument> = emptyList()
  var mapping: MappingDocument? = null
  lateinit var createdAt: Instant
  lateinit var lastChangedAt: Instant
}

class FilterRuleDocument {
  lateinit var mode: String
  lateinit var sourcePath: String
  lateinit var operator: String
  var value: String? = null
  var includeAbsent: Boolean = false
}

class MappingDocument {
  var fieldMappings: List<FieldMappingDocument> = emptyList()
}

class FieldMappingDocument {
  lateinit var targetPropertyId: String
  var sourcePath: String? = null
  lateinit var conversion: String
  var conversionUnit: String? = null
  var fallbackValue: String? = null
  var referenceLookup: ReferenceLookupDocument? = null
}

class ReferenceLookupDocument {
  var criteria: List<ReferenceLookupCriterionDocument> = emptyList()
}

class ReferenceLookupCriterionDocument {
  lateinit var targetPropertyId: String
  lateinit var sourcePath: String
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
