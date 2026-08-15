package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.imports.DataPath
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.FieldMappingConversion
import de.chrgroth.james.platform.domain.model.imports.ImportConnectionId
import de.chrgroth.james.platform.domain.model.imports.ImportJob
import de.chrgroth.james.platform.domain.model.imports.ImportJobId
import de.chrgroth.james.platform.domain.model.imports.ImportStatus
import de.chrgroth.james.platform.domain.model.imports.Mapping
import de.chrgroth.james.platform.domain.model.imports.NumericRange
import de.chrgroth.james.platform.domain.model.imports.ReferenceLookup
import de.chrgroth.james.platform.domain.model.imports.ReferenceLookupCriterion
import de.chrgroth.james.platform.domain.model.imports.SchemaProperty
import de.chrgroth.james.platform.domain.model.imports.SchemaPropertyType
import de.chrgroth.james.platform.domain.port.out.imports.ImportJobRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant

@ApplicationScoped
class ImportJobRepositoryAdapter(
  private val importJobDocumentRepository: ImportJobDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : ImportJobRepositoryPort {

  override fun findAllByUserId(userId: String): List<ImportJob> =
    mongoQueryMetrics.timed("import_job.findAllByUserId") {
      importJobDocumentRepository.find(USER_ID_FIELD, userId).list().map { it.toDomain() }
    }

  override fun findById(id: ImportJobId): ImportJob? =
    mongoQueryMetrics.timed("import_job.findById") {
      importJobDocumentRepository.findById(id.value)?.toDomain()
    }

  override fun save(importJob: ImportJob) {
    mongoQueryMetrics.timed("import_job.save") {
      val doc = importJob.toDocument()
      importJobDocumentRepository.mongoCollection().replaceOne(
        Filters.eq(ID_FIELD, importJob.id.value),
        doc,
        ReplaceOptions().upsert(true),
      )
    }
  }

  override fun delete(id: ImportJobId) {
    mongoQueryMetrics.timed("import_job.delete") {
      importJobDocumentRepository.deleteById(id.value)
    }
  }

  override fun deleteAllLastChangedBefore(cutoff: Instant): Long =
    mongoQueryMetrics.timed("import_job.deleteAllLastChangedBefore") {
      importJobDocumentRepository.mongoCollection().deleteMany(Filters.lt(LAST_CHANGED_AT_FIELD, cutoff)).deletedCount
    }

  private fun ImportJobDocument.toDomain() = ImportJob(
    id = ImportJobId(id),
    userId = userId,
    installedAppId = InstalledAppId(installedAppId),
    connectionId = ImportConnectionId(connectionId),
    targetEntityDefinitionId = EntityDefinitionId(targetEntityDefinitionId),
    status = ImportStatus.valueOf(status),
    payload = payload,
    detectedDataPaths = detectedDataPaths.map { it.toDomain() },
    selectedDataPath = selectedDataPath,
    detectedSchema = detectedSchema.map { it.toDomain() },
    mapping = mapping?.toDomain(),
    createdAt = createdAt,
    lastChangedAt = lastChangedAt,
  )

  private fun DataPathDocument.toDomain() = DataPath(
    path = path,
    size = size,
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

  private fun MappingDocument.toDomain() = Mapping(
    fieldMappings = fieldMappings.map { it.toDomain() },
  )

  private fun FieldMappingDocument.toDomain() = FieldMapping(
    targetPropertyId = PropertyId(targetPropertyId),
    sourcePath = sourcePath,
    conversion = FieldMappingConversion.valueOf(conversion),
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

  private fun ImportJob.toDocument() = ImportJobDocument().also { doc ->
    doc.id = id.value
    doc.userId = userId
    doc.installedAppId = installedAppId.value
    doc.connectionId = connectionId.value
    doc.targetEntityDefinitionId = targetEntityDefinitionId.value
    doc.status = status.name
    doc.payload = payload
    doc.detectedDataPaths = detectedDataPaths.map { it.toDocument() }
    doc.selectedDataPath = selectedDataPath
    doc.detectedSchema = detectedSchema.map { it.toDocument() }
    doc.mapping = mapping?.toDocument()
    doc.createdAt = createdAt
    doc.lastChangedAt = lastChangedAt
  }

  private fun DataPath.toDocument() = DataPathDocument().also { doc ->
    doc.path = path
    doc.size = size
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

  private fun Mapping.toDocument() = MappingDocument().also { doc ->
    doc.fieldMappings = fieldMappings.map { it.toDocument() }
  }

  private fun FieldMapping.toDocument() = FieldMappingDocument().also { doc ->
    doc.targetPropertyId = targetPropertyId.value
    doc.sourcePath = sourcePath
    doc.conversion = conversion.name
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

  companion object {
    internal const val ID_FIELD = "_id"
    internal const val USER_ID_FIELD = "userId"
    internal const val LAST_CHANGED_AT_FIELD = "lastChangedAt"
  }
}
