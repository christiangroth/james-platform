package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.FieldMappingConversion
import de.chrgroth.james.platform.domain.model.imports.FilterMode
import de.chrgroth.james.platform.domain.model.imports.FilterOperator
import de.chrgroth.james.platform.domain.model.imports.FilterRule
import de.chrgroth.james.platform.domain.model.imports.ImportConnectionId
import de.chrgroth.james.platform.domain.model.imports.ImportDefinition
import de.chrgroth.james.platform.domain.model.imports.ImportDefinitionId
import de.chrgroth.james.platform.domain.model.imports.Mapping
import de.chrgroth.james.platform.domain.model.imports.NumericRange
import de.chrgroth.james.platform.domain.model.imports.ReferenceLookup
import de.chrgroth.james.platform.domain.model.imports.ReferenceLookupCriterion
import de.chrgroth.james.platform.domain.model.imports.SchemaProperty
import de.chrgroth.james.platform.domain.model.imports.SchemaPropertyType
import de.chrgroth.james.platform.domain.port.out.imports.ImportDefinitionRepositoryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ImportDefinitionRepositoryAdapter(
  private val importDefinitionDocumentRepository: ImportDefinitionDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : ImportDefinitionRepositoryPort {

  override fun findAllByUserId(userId: String): List<ImportDefinition> =
    mongoQueryMetrics.timed("import_definition.findAllByUserId") {
      importDefinitionDocumentRepository.find(USER_ID_FIELD, userId).list().map { it.toDomain() }
    }

  override fun findById(id: ImportDefinitionId): ImportDefinition? =
    mongoQueryMetrics.timed("import_definition.findById") {
      importDefinitionDocumentRepository.findById(id.value)?.toDomain()
    }

  override fun findAllWithScheduleSet(): List<ImportDefinition> =
    mongoQueryMetrics.timed("import_definition.findAllWithScheduleSet") {
      importDefinitionDocumentRepository.find("$SCHEDULE_FIELD is not null").list().map { it.toDomain() }
    }

  override fun save(importDefinition: ImportDefinition) {
    mongoQueryMetrics.timed("import_definition.save") {
      val doc = importDefinition.toDocument()
      importDefinitionDocumentRepository.mongoCollection().replaceOne(
        Filters.eq(ID_FIELD, importDefinition.id.value),
        doc,
        ReplaceOptions().upsert(true),
      )
    }
  }

  override fun delete(id: ImportDefinitionId) {
    mongoQueryMetrics.timed("import_definition.delete") {
      importDefinitionDocumentRepository.deleteById(id.value)
    }
  }

  private fun ImportDefinitionDocument.toDomain() = ImportDefinition(
    id = ImportDefinitionId(id),
    userId = userId,
    connectionId = ImportConnectionId(connectionId),
    name = name,
    urlPostfix = urlPostfix,
    targetEntityDefinitionId = EntityDefinitionId(targetEntityDefinitionId),
    selectedDataPath = selectedDataPath,
    filterRules = filterRules.map { it.toDomain() },
    mapping = mapping?.toDomain(),
    schedule = schedule,
    lastRunAt = lastRunAt,
    lastKnownSchema = lastKnownSchema.map { it.toDomain() },
    notifyOnSlack = notifyOnSlack,
    createdAt = createdAt,
    lastChangedAt = lastChangedAt,
  )

  private fun FilterRuleDocument.toDomain() = FilterRule(
    mode = FilterMode.valueOf(mode),
    sourcePath = sourcePath,
    operator = FilterOperator.valueOf(operator),
    value = value,
    includeAbsent = includeAbsent,
  )

  private fun MappingDocument.toDomain() = Mapping(
    fieldMappings = fieldMappings.map { it.toDomain() },
  )

  private fun FieldMappingDocument.toDomain() = FieldMapping(
    targetPropertyId = PropertyId(targetPropertyId),
    sourcePath = sourcePath,
    conversion = FieldMappingConversion.valueOf(conversion),
    importGranularity = importGranularity,
    fallbackValue = fallbackValue,
    referenceLookup = referenceLookup?.toDomain(),
  )

  private fun ReferenceLookupDocument.toDomain() = ReferenceLookup(
    criteria = criteria.map { it.toDomain() },
  )

  private fun ReferenceLookupCriterionDocument.toDomain() = ReferenceLookupCriterion(
    targetPropertyId = PropertyId(targetPropertyId),
    sourcePath = sourcePath,
  )

  private fun SchemaPropertyDocument.toDomain() = SchemaProperty(
    path = path,
    typeCounts = typeCounts.mapKeys { SchemaPropertyType.valueOf(it.key) },
    mandatory = mandatory,
    numericRange = numericRange?.toDomain(),
    stringLengthCounts = stringLengthCounts.mapKeys { it.key.toInt() },
  )

  private fun NumericRangeDocument.toDomain() = NumericRange(
    min = min,
    max = max,
  )

  private fun ImportDefinition.toDocument() = ImportDefinitionDocument().also { doc ->
    doc.id = id.value
    doc.userId = userId
    doc.connectionId = connectionId.value
    doc.name = name
    doc.urlPostfix = urlPostfix
    doc.targetEntityDefinitionId = targetEntityDefinitionId.value
    doc.selectedDataPath = selectedDataPath
    doc.filterRules = filterRules.map { it.toDocument() }
    doc.mapping = mapping?.toDocument()
    doc.schedule = schedule
    doc.lastRunAt = lastRunAt
    doc.lastKnownSchema = lastKnownSchema.map { it.toDocument() }
    doc.notifyOnSlack = notifyOnSlack
    doc.createdAt = createdAt
    doc.lastChangedAt = lastChangedAt
  }

  private fun FilterRule.toDocument() = FilterRuleDocument().also { doc ->
    doc.mode = mode.name
    doc.sourcePath = sourcePath
    doc.operator = operator.name
    doc.value = value
    doc.includeAbsent = includeAbsent
  }

  private fun Mapping.toDocument() = MappingDocument().also { doc ->
    doc.fieldMappings = fieldMappings.map { it.toDocument() }
  }

  private fun FieldMapping.toDocument() = FieldMappingDocument().also { doc ->
    doc.targetPropertyId = targetPropertyId.value
    doc.sourcePath = sourcePath
    doc.conversion = conversion.name
    doc.importGranularity = importGranularity
    doc.fallbackValue = fallbackValue
    doc.referenceLookup = referenceLookup?.toDocument()
  }

  private fun ReferenceLookup.toDocument() = ReferenceLookupDocument().also { doc ->
    doc.criteria = criteria.map { it.toDocument() }
  }

  private fun ReferenceLookupCriterion.toDocument() = ReferenceLookupCriterionDocument().also { doc ->
    doc.targetPropertyId = targetPropertyId.value
    doc.sourcePath = sourcePath
  }

  private fun SchemaProperty.toDocument() = SchemaPropertyDocument().also { doc ->
    doc.path = path
    doc.typeCounts = typeCounts.mapKeys { it.key.name }
    doc.mandatory = mandatory
    doc.numericRange = numericRange?.toDocument()
    doc.stringLengthCounts = stringLengthCounts.mapKeys { it.key.toString() }
  }

  private fun NumericRange.toDocument() = NumericRangeDocument().also { doc ->
    doc.min = min
    doc.max = max
  }

  companion object {
    internal const val ID_FIELD = "_id"
    internal const val USER_ID_FIELD = "userId"
    internal const val SCHEDULE_FIELD = "schedule"
  }
}
