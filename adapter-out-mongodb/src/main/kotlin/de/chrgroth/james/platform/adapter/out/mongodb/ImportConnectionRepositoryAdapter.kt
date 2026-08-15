package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import de.chrgroth.james.platform.domain.model.imports.ImportConnection
import de.chrgroth.james.platform.domain.model.imports.ImportConnectionId
import de.chrgroth.james.platform.domain.port.out.imports.ImportConnectionRepositoryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ImportConnectionRepositoryAdapter(
  private val importConnectionDocumentRepository: ImportConnectionDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : ImportConnectionRepositoryPort {

  override fun findAllByUserId(userId: String): List<ImportConnection> =
    mongoQueryMetrics.timed("import_connection.findAllByUserId") {
      importConnectionDocumentRepository.find(USER_ID_FIELD, userId).list().map { it.toDomain() }
    }

  override fun findById(id: ImportConnectionId): ImportConnection? =
    mongoQueryMetrics.timed("import_connection.findById") {
      importConnectionDocumentRepository.findById(id.value)?.toDomain()
    }

  override fun save(importConnection: ImportConnection) {
    mongoQueryMetrics.timed("import_connection.save") {
      val doc = importConnection.toDocument()
      importConnectionDocumentRepository.mongoCollection().replaceOne(
        Filters.eq(ID_FIELD, importConnection.id.value),
        doc,
        ReplaceOptions().upsert(true),
      )
    }
  }

  override fun delete(id: ImportConnectionId) {
    mongoQueryMetrics.timed("import_connection.delete") {
      importConnectionDocumentRepository.deleteById(id.value)
    }
  }

  private fun ImportConnectionDocument.toDomain() = ImportConnection(
    id = ImportConnectionId(id),
    userId = userId,
    name = name,
    baseUrl = url,
    encryptedBearerToken = encryptedBearerToken,
    createdAt = createdAt,
    lastChangedAt = lastChangedAt,
  )

  private fun ImportConnection.toDocument() = ImportConnectionDocument().also { doc ->
    doc.id = id.value
    doc.userId = userId
    doc.name = name
    doc.url = baseUrl
    doc.encryptedBearerToken = encryptedBearerToken
    doc.createdAt = createdAt
    doc.lastChangedAt = lastChangedAt
  }

  companion object {
    internal const val ID_FIELD = "_id"
    internal const val USER_ID_FIELD = "userId"
  }
}
