package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.imports.DataPath
import de.chrgroth.james.platform.domain.model.imports.FieldMappingConversion
import de.chrgroth.james.platform.domain.model.imports.ImportDefinitionId
import de.chrgroth.james.platform.domain.model.imports.ImportJob
import de.chrgroth.james.platform.domain.model.imports.ImportJobId
import de.chrgroth.james.platform.domain.model.imports.ImportStatus
import de.chrgroth.james.platform.domain.model.imports.NumericRange
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

  override fun migrateLongToDurationFieldMappingConversion() {
    mongoQueryMetrics.timed("import_job.migrateLongToDurationFieldMappingConversion") {
      importJobDocumentRepository.mongoCollection().updateMany(
        Filters.eq(FIELD_MAPPINGS_CONVERSION_FIELD, LEGACY_LONG_TO_DURATION_CONVERSION),
        Updates.set(FIELD_MAPPINGS_MATCHED_CONVERSION_FIELD, FieldMappingConversion.NONE.name),
        UpdateOptions().arrayFilters(listOf(Filters.eq(MATCHED_ELEMENT_CONVERSION_FIELD, LEGACY_LONG_TO_DURATION_CONVERSION))),
      )
    }
  }

  private fun ImportJobDocument.toDomain() = ImportJob(
    id = ImportJobId(id),
    userId = userId,
    installedAppId = InstalledAppId(installedAppId),
    importDefinitionId = ImportDefinitionId(importDefinitionId),
    status = ImportStatus.valueOf(status),
    payload = payload,
    detectedDataPaths = detectedDataPaths.map { it.toDomain() },
    detectedSchema = detectedSchema.map { it.toDomain() },
    filteredSchema = filteredSchema.map { it.toDomain() },
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

  private fun ImportJob.toDocument() = ImportJobDocument().also { doc ->
    doc.id = id.value
    doc.userId = userId
    doc.installedAppId = installedAppId.value
    doc.importDefinitionId = importDefinitionId.value
    doc.status = status.name
    doc.payload = payload
    doc.detectedDataPaths = detectedDataPaths.map { it.toDocument() }
    doc.detectedSchema = detectedSchema.map { it.toDocument() }
    doc.filteredSchema = filteredSchema.map { it.toDocument() }
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

  companion object {
    internal const val ID_FIELD = "_id"
    internal const val USER_ID_FIELD = "userId"
    internal const val LAST_CHANGED_AT_FIELD = "lastChangedAt"
    internal const val FIELD_MAPPINGS_CONVERSION_FIELD = "mapping.fieldMappings.conversion"
    internal const val FIELD_MAPPINGS_MATCHED_CONVERSION_FIELD = "mapping.fieldMappings.\$[elem].conversion"
    internal const val MATCHED_ELEMENT_CONVERSION_FIELD = "elem.conversion"

    // ADR 0017: FieldMappingConversion.LONG_TO_DURATION was removed together with the DURATION property type; see
    // migrateLongToDurationFieldMappingConversion(). Kept operating on the legacy import_job.mapping subdocument
    // path (rather than moving to import_definition) since this one-off cleanup already completed against
    // production data before ADR 0021 split mapping out of ImportJob - see docs/adr/0021-import-definition-job-split.md.
    internal const val LEGACY_LONG_TO_DURATION_CONVERSION = "LONG_TO_DURATION"
  }
}
