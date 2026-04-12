package de.chrgroth.james.platform.domain.port.`in`.infra

import de.chrgroth.james.platform.domain.model.viewer.MongoViewerFilter
import de.chrgroth.james.platform.domain.model.viewer.MongoViewerResult

interface MongoViewerPort {
  fun getViewer(
    collection: String?,
    filters: List<MongoViewerFilter>,
    sortField: String?,
    sortDesc: Boolean,
    page: Int,
    pageSize: Int,
  ): MongoViewerResult
}
