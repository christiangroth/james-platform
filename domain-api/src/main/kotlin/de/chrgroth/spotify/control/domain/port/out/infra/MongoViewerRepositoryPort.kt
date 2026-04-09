package de.chrgroth.spotify.control.domain.port.out.infra

import de.chrgroth.spotify.control.domain.model.viewer.MongoViewerFilter

interface MongoViewerRepositoryPort {
  fun listCollections(): List<String>
  fun sampleFieldNames(collection: String): List<String>
  fun countDocuments(collection: String, filters: List<MongoViewerFilter>): Long
  fun queryDocuments(
    collection: String,
    filters: List<MongoViewerFilter>,
    sortField: String?,
    sortDesc: Boolean,
    skip: Long,
    limit: Int,
  ): List<String>
}
