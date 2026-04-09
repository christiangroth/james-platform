package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.viewer.MongoViewerField
import de.chrgroth.spotify.control.domain.model.viewer.MongoViewerFieldType
import de.chrgroth.spotify.control.domain.model.viewer.MongoViewerFilter
import de.chrgroth.spotify.control.domain.model.viewer.MongoViewerFilterOperator
import de.chrgroth.spotify.control.domain.model.viewer.MongoViewerResult
import de.chrgroth.spotify.control.domain.port.`in`.infra.MongoViewerPort
import de.chrgroth.spotify.control.domain.port.out.infra.MongoViewerRepositoryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class MongoViewerService(
  private val repository: MongoViewerRepositoryPort,
) : MongoViewerPort {

  override fun getViewer(
    collection: String?,
    filters: List<MongoViewerFilter>,
    sortField: String?,
    sortDesc: Boolean,
    page: Int,
    pageSize: Int,
  ): MongoViewerResult {
    val collections = repository.listCollections()
    if (collection.isNullOrBlank()) {
      return emptyResult(collections, sortField, sortDesc, pageSize)
    }
    return buildResult(collections, collection, filters, sortField, sortDesc, page, pageSize)
  }

  private fun emptyResult(
    collections: List<String>,
    sortField: String?,
    sortDesc: Boolean,
    pageSize: Int,
  ) = MongoViewerResult(
    collections = collections,
    selectedCollection = "",
    fields = emptyList(),
    sortField = sortField ?: "",
    sortDesc = sortDesc,
    documents = emptyList(),
    totalCount = 0,
    page = 1,
    pageSize = pageSize,
    totalPages = 0,
    hasPrev = false,
    hasNext = false,
    prevPage = 1,
    nextPage = 1,
  )

  private fun buildResult(
    collections: List<String>,
    collection: String,
    filters: List<MongoViewerFilter>,
    sortField: String?,
    sortDesc: Boolean,
    page: Int,
    pageSize: Int,
  ): MongoViewerResult {
    val fields = buildFields(collection, filters)
    val totalCount = repository.countDocuments(collection, filters)
    val totalPages = if (pageSize > 0) ((totalCount + pageSize - 1) / pageSize).toInt() else 0
    val effectivePage = page.coerceIn(1, if (totalPages > 0) totalPages else 1)
    val documents = repository.queryDocuments(
      collection = collection,
      filters = filters,
      sortField = sortField,
      sortDesc = sortDesc,
      skip = ((effectivePage - 1) * pageSize).toLong(),
      limit = pageSize,
    )
    return MongoViewerResult(
      collections = collections,
      selectedCollection = collection,
      fields = fields,
      sortField = sortField ?: "",
      sortDesc = sortDesc,
      documents = documents,
      totalCount = totalCount,
      page = effectivePage,
      pageSize = pageSize,
      totalPages = totalPages,
      hasPrev = effectivePage > 1,
      hasNext = effectivePage < totalPages,
      prevPage = (effectivePage - 1).coerceAtLeast(1),
      nextPage = (effectivePage + 1).coerceAtMost(if (totalPages > 0) totalPages else 1),
    )
  }

  private fun buildFields(collection: String, filters: List<MongoViewerFilter>): List<MongoViewerField> =
    repository.sampleFieldNames(collection).map { fieldName ->
      val type = if (isIdField(fieldName)) MongoViewerFieldType.ID else MongoViewerFieldType.STRING
      MongoViewerField(
        name = fieldName,
        fieldType = type,
        containsValue = filters.filterValue(fieldName, MongoViewerFilterOperator.CONTAINS),
        equalsValue = filters.filterValue(fieldName, MongoViewerFilterOperator.EQUALS),
        inValue = filters.filterValue(fieldName, MongoViewerFilterOperator.IN),
        notInValue = filters.filterValue(fieldName, MongoViewerFilterOperator.NOT_IN),
      )
    }

  private fun List<MongoViewerFilter>.filterValue(field: String, operator: MongoViewerFilterOperator): String =
    find { it.field == field && it.operator == operator }?.value ?: ""

  private fun isIdField(name: String): Boolean =
    name == "_id" || name.endsWith("Id") || name.endsWith("Ids")
}
