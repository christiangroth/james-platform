package de.chrgroth.spotify.control.domain.port.`in`.infra

import de.chrgroth.spotify.control.domain.model.viewer.MongoViewerFilter
import de.chrgroth.spotify.control.domain.model.viewer.MongoViewerResult

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
