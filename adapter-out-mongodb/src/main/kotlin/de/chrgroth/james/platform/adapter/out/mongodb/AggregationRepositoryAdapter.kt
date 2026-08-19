package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Updates
import de.chrgroth.james.platform.domain.model.app.AggregationDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValue
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValueId
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValueStatus
import de.chrgroth.james.platform.domain.port.out.readmodel.AggregationRepositoryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class AggregationRepositoryAdapter(
  private val aggregationValueDocumentRepository: AggregationValueDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : AggregationRepositoryPort {

  override fun findAllByInstalledAppIdAndAggregationDefinitionId(installedAppId: InstalledAppId, aggregationDefinitionId: AggregationDefinitionId): List<AggregationValue> =
    mongoQueryMetrics.timed("aggregation.findAllByInstalledAppIdAndAggregationDefinitionId") {
      aggregationValueDocumentRepository.find(
        Filters.and(
          Filters.eq(INSTALLED_APP_ID_FIELD, installedAppId.value),
          Filters.eq(AGGREGATION_DEFINITION_ID_FIELD, aggregationDefinitionId.value),
        ),
      ).list().map { it.toDomain() }
    }

  override fun save(value: AggregationValue) {
    mongoQueryMetrics.timed("aggregation.save") {
      val doc = value.toDocument()
      aggregationValueDocumentRepository.mongoCollection().replaceOne(
        Filters.eq(ID_FIELD, doc.id),
        doc,
        ReplaceOptions().upsert(true),
      )
    }
  }

  override fun markStaleByInstalledAppIdAndAggregationDefinitionId(installedAppId: InstalledAppId, aggregationDefinitionId: AggregationDefinitionId) {
    mongoQueryMetrics.timed("aggregation.markStaleByInstalledAppIdAndAggregationDefinitionId") {
      aggregationValueDocumentRepository.mongoCollection().updateMany(
        Filters.and(
          Filters.eq(INSTALLED_APP_ID_FIELD, installedAppId.value),
          Filters.eq(AGGREGATION_DEFINITION_ID_FIELD, aggregationDefinitionId.value),
        ),
        Updates.set(STATUS_FIELD, AggregationValueStatus.STALE.name),
      )
    }
  }

  override fun deleteAllByInstalledAppId(installedAppId: InstalledAppId) {
    mongoQueryMetrics.timed("aggregation.deleteAllByInstalledAppId") {
      aggregationValueDocumentRepository.mongoCollection().deleteMany(Filters.eq(INSTALLED_APP_ID_FIELD, installedAppId.value))
    }
  }

  override fun deleteAllByInstalledAppIdAndAggregationDefinitionId(installedAppId: InstalledAppId, aggregationDefinitionId: AggregationDefinitionId) {
    mongoQueryMetrics.timed("aggregation.deleteAllByInstalledAppIdAndAggregationDefinitionId") {
      aggregationValueDocumentRepository.mongoCollection().deleteMany(
        Filters.and(
          Filters.eq(INSTALLED_APP_ID_FIELD, installedAppId.value),
          Filters.eq(AGGREGATION_DEFINITION_ID_FIELD, aggregationDefinitionId.value),
        ),
      )
    }
  }

  private fun AggregationValueDocument.toDomain() = AggregationValue(
    id = AggregationValueId(
      installedAppId = InstalledAppId(installedAppId),
      aggregationDefinitionId = AggregationDefinitionId(aggregationDefinitionId),
      groupKey = groupKey,
      bucketKey = bucketKey,
    ),
    value = value,
    status = AggregationValueStatus.valueOf(status),
    updatedAt = updatedAt,
  )

  private fun AggregationValue.toDocument() = AggregationValueDocument().also { doc ->
    doc.id = compositeId(id)
    doc.installedAppId = id.installedAppId.value
    doc.aggregationDefinitionId = id.aggregationDefinitionId.value
    doc.groupKey = id.groupKey
    doc.bucketKey = id.bucketKey
    doc.value = value
    doc.status = status.name
    doc.updatedAt = updatedAt
  }

  private fun compositeId(id: AggregationValueId): String =
    listOf(id.installedAppId.value, id.aggregationDefinitionId.value, id.groupKey.orEmpty(), id.bucketKey.orEmpty()).joinToString(ID_SEPARATOR)

  companion object {
    internal const val ID_FIELD = "_id"
    internal const val INSTALLED_APP_ID_FIELD = "installedAppId"
    internal const val AGGREGATION_DEFINITION_ID_FIELD = "aggregationDefinitionId"
    internal const val STATUS_FIELD = "status"
    // Unit separator control character (U+001F), matching the convention already used for AppData's LIST_VALUE_SEPARATOR.
    private const val ID_SEPARATOR = ""
  }
}
