package de.chrgroth.james.platform.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_version")
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
  var properties: List<PropertyDocument> = emptyList()
}

class PropertyDocument {
  lateinit var id: String
  lateinit var name: String
  lateinit var type: String
  var nullable: Boolean = true
  var constraints: List<ConstraintDocument> = emptyList()
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
  var pages: List<ReportPageDocument> = emptyList()
}

class ReportPageDocument {
  lateinit var html: String
  lateinit var script: String
  var entityFilters: List<ReportEntityFilterDocument> = emptyList()
}

class ReportEntityFilterDocument {
  lateinit var entityId: String
  var filterExpression: String? = null
}
