package de.chrgroth.james.platform.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_app_version")
class AppVersionDocument {

  @BsonId
  lateinit var id: String
  lateinit var appId: String
  var versionNumber: String? = null
  var releaseNotes: String? = null
  var entityDefinitions: List<EntityDefinitionDocument> = emptyList()
  var reports: List<ReportDocument> = emptyList()
  lateinit var status: String
  lateinit var createdAt: Instant
}

class EntityDefinitionDocument {
  lateinit var id: String
  lateinit var name: String
  var displayText: String? = null
  var properties: List<PropertyDocument> = emptyList()
  var sortBy: List<SortCriteriaDocument> = emptyList()
  var computedProperties: List<ComputedPropertyDocument> = emptyList()
  var aggregations: List<AggregationDefinitionDocument> = emptyList()
  var migrationScript: String? = null
}

class SortCriteriaDocument {
  lateinit var propertyId: String
  lateinit var direction: String
}

class PropertyDocument {
  lateinit var id: String
  lateinit var name: String
  lateinit var type: String
  var nullable: Boolean = true
  var constraints: List<ConstraintDocument> = emptyList()
  var default: String? = null
  var smartDefault: String? = null
  var valueProposals: List<String> = emptyList()
  var targetEntityId: String? = null
  var listItemType: String? = null
  var itemConstraints: List<ConstraintDocument> = emptyList()
  var nestedProperties: List<PropertyDocument> = emptyList()
  var unit: PropertyUnitDocument? = null
}

class PropertyUnitDocument {
  lateinit var family: String
  lateinit var storageGranularity: String
  lateinit var defaultGranularity: String
}

class ConstraintDocument {
  lateinit var constraintType: String
  var longValue: Long? = null
  var doubleValue: Double? = null
  var intValue: Int? = null
  var stringValue: String? = null
}

class ReportDocument {
  lateinit var id: String
  lateinit var name: String
  var html: String = ""
  var script: String = ""
}

class ComputedPropertyDocument {
  lateinit var id: String
  lateinit var name: String
  lateinit var type: String
  var script: String? = null
}

class AggregationDefinitionDocument {
  lateinit var id: String
  lateinit var name: String
  lateinit var function: String
  lateinit var sourceProperty: String
  var refPath: String? = null
  var timeBucket: String? = null
  var timeProperty: String? = null
  var groupBy: String? = null
}
