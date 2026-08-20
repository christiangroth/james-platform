package de.chrgroth.james.platform.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "import_definition")
class ImportDefinitionDocument {

  @BsonId
  lateinit var id: String
  lateinit var userId: String
  lateinit var connectionId: String
  lateinit var name: String
  var urlPostfix: String? = null
  lateinit var targetEntityDefinitionId: String
  var selectedDataPath: String? = null
  var filterRules: List<FilterRuleDocument> = emptyList()
  var mapping: MappingDocument? = null
  var schedule: String? = null
  var lastRunAt: Instant? = null
  var lastKnownSchema: List<SchemaPropertyDocument> = emptyList()
  var notifyOnSlack: Boolean = false
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
  var importGranularity: String? = null
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
