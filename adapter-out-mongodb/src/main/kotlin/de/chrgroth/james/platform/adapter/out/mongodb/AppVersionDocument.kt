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
  // Documents written before ADR 0029 may still carry a `migrationScript` (Kotlin): the POJO codec skips it on read, it is no longer supported.
  var migrationSteps: List<MigrationStepDocument> = emptyList()
  var configuration: Boolean = false
}

class MigrationStepDocument {
  lateinit var id: String
  // "CONVERT_TYPE", "COPY_VALUE", "CONVERT_UNIT", "FILL_EMPTY_VALUE" or "ADJUST_TO_CONSTRAINTS"
  lateinit var type: String
  var propertyId: String? = null
  var sourcePropertyId: String? = null
  var targetPropertyId: String? = null
  // Used by CONVERT_UNIT only.
  var sourceGranularityFamily: String? = null
  var sourceGranularityName: String? = null
  // Used by FILL_EMPTY_VALUE only; null means "use the property's own default".
  var value: String? = null
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

  // Kotlin smart default script written before ADR 0029: read as a legacy script (not evaluated), written back unchanged until replaced.
  var smartDefault: String? = null

  // Smart default expression (ADR 0029).
  var smartDefaultExpression: String? = null
  var valueProposals: List<String> = emptyList()
  var targetEntityId: String? = null
  var listItemType: String? = null
  var itemConstraints: List<ConstraintDocument> = emptyList()
  var nestedProperties: List<PropertyDocument> = emptyList()
  var unit: PropertyUnitDocument? = null
  var referenceLookupPropertyId: String? = null
  var referenceLookupAutoCreate: Boolean = false
}

class PropertyUnitDocument {
  lateinit var family: String
  lateinit var storageGranularity: String
  lateinit var defaultGranularity: String

  // Optional, missing in documents stored before it existed: the largest granularity a composite value is shown in.
  var maxDisplayGranularity: String? = null
}

class ConstraintDocument {
  lateinit var constraintType: String
  var longValue: Long? = null
  var doubleValue: Double? = null
  var intValue: Int? = null
  var stringValue: String? = null
}

/**
 * A report's YAML definition (see arc42 "Reports"). Documents written before the declarative reports still carry the former `html`
 * and `script` fields: the POJO codec skips them on read, so those reports load with an empty definition (they were never executable).
 */
class ReportDocument {
  lateinit var id: String
  lateinit var name: String
  var definition: String = ""
}

class ComputedPropertyDocument {
  lateinit var id: String
  lateinit var name: String
  lateinit var type: String

  // Kotlin script written before ADR 0029: read as a legacy script (not evaluated), written back unchanged until replaced.
  var script: String? = null

  // Expression (ADR 0029) and the unit of its numeric result.
  var expression: String? = null
  var unit: PropertyUnitDocument? = null
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
  var periodStartDay: Int? = null
  var periodStartMonth: Int? = null
  var sourcePath: List<String>? = null
}
